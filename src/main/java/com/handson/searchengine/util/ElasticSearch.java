package com.handson.searchengine.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.handson.searchengine.model.SearchResultDto;
import com.handson.searchengine.model.UrlSearchDoc;
import okhttp3.*;
import org.apache.tomcat.util.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
public class ElasticSearch {
    private static final Logger logger = LoggerFactory.getLogger(ElasticSearch.class);

    // 3 seconds timeout configuration for stable and safe fail-safe fallback triggers
    OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .build();

    @Value("${elasticsearch.base.url}")
    private String ELASTIC_SEARCH_URL;

    @Value("${elasticsearch.key}")
    private String API_KEY;

    @Value("${elasticsearch.index}")
    private String index;

    @Autowired
    ObjectMapper om;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * מוסיף מסמך חדש לאינדקס
     */
    public void addData(UrlSearchDoc doc) throws IOException {
        String auth = new String(Base64.encodeBase64(API_KEY.getBytes()));
        String json = om.writeValueAsString(doc);

        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                json
        );

        Request request = new Request.Builder()
                .url(ELASTIC_SEARCH_URL + "/" + index + "/_doc")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader(HttpHeaders.AUTHORIZATION, "Basic " + auth)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.error("Failed to add data to Elasticsearch: {} {}", response.code(), response.message());
            } else {
                logger.debug("Document added to Elasticsearch successfully.");
            }
        }
    }

    /**
     * חיפוש עם סינון כדי להוציא עמודי בית ועם highlight.
     * במקרה של שגיאה או Timeout של שנייה אחת, מופעל מנגנון הגיבוי מול Redis.
     */
    public List<SearchResultDto> search(String query) throws IOException {
        List<SearchResultDto> results = new ArrayList<>();
        String auth = new String(Base64.encodeBase64(API_KEY.getBytes()));

        String requestBody = "{\n" +
                "  \"size\": 80,\n" +
                "  \"query\": {\n" +
                "    \"bool\": {\n" +
                "      \"must\": [\n" +
                "        { \"multi_match\": { \"query\": \"" + escapeJson(query) + "\", \"fields\": [\"title^3\", \"content\"] } }\n" +
                "      ]\n" +
                "    }\n" +
                "  },\n" +
                "  \"highlight\": { \"pre_tags\": [\"<em>\"], \"post_tags\": [\"</em>\"], \"fields\": { \"content\": {}, \"title\": {} } }\n" +
                "}";

        RequestBody body = RequestBody.create(MediaType.parse("application/json"), requestBody);
        Request request = new Request.Builder()
                .url(ELASTIC_SEARCH_URL + "/" + index + "/_search")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader(HttpHeaders.AUTHORIZATION, "Basic " + auth)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.error("Search failed: {}. Triggering Redis hybrid fallback search.", response.code());
                return performFallbackRedisSearch(query);
            }

            String resp = response.body().string();
            Map<String, Object> map = om.readValue(resp, Map.class);
            Map<String, Object> hitsMap = (Map<String, Object>) map.get("hits");
            List<Map<String, Object>> hits = hitsMap == null ? null : (List<Map<String, Object>>) hitsMap.get("hits");
            if (hits == null) return results;

            Set<String> seen = new LinkedHashSet<>();
            List<SearchResultDto> articleCandidates = new ArrayList<>();
            List<SearchResultDto> others = new ArrayList<>();

            for (Map<String, Object> hit : hits) {
                Map<String, Object> src = (Map<String, Object>) hit.get("_source");
                if (src == null) continue;
                String url = (String) src.get("url");
                if (url == null) continue;
                if (seen.contains(url)) continue;

                // Skip homepages and prioritize articles
                if (isHomepage(url)) {
                    continue;
                }

                String snippet = extractSnippet(hit);
                SearchResultDto dto = new SearchResultDto(url, snippet);

                if (isLikelyArticle(url)) {
                    articleCandidates.add(dto);
                } else {
                    others.add(dto);
                }
                seen.add(url);
            }

            results.addAll(articleCandidates);
            for (SearchResultDto dto : others) {
                if (results.size() >= 50) break;
                results.add(dto);
            }
        } catch (Exception e) {
            logger.warn("Elasticsearch lookup failed due to network or timeout issues ({}). Triggering Redis fallback.", e.getMessage());
            return performFallbackRedisSearch(query);
        }
        return results;
    }

    /**
     * מנגנון הגיבוי המהיר: שליפת כל הנתונים השמורים ב-Redis עבור מזהה ה-Crawl,
     * וביצוע חיפוש טקסטואלי עם Highlight מותאם ב-Java.
     * מונע החזרת כפילויות של כתובות URL בריצות זחילה שונות.
     */
    private List<SearchResultDto> performFallbackRedisSearch(String query) {
        List<SearchResultDto> fallbackResults = new ArrayList<>();
        try {
            logger.info("Initializing Redis fallback search for query: {}", query);

            // 1. Find all active crawl statuses to identify active/recent crawls
            Set<String> keys = redisTemplate.keys("*.data");
            if (keys == null || keys.isEmpty()) {
                logger.warn("No crawled data found in Redis fallback DB.");
                return fallbackResults;
            }

            String lowerQuery = query.toLowerCase(Locale.ROOT);
            List<SearchResultDto> rawResults = new ArrayList<>();

            // 2. Scan available crawl hashes in Redis and search content inside
            for (String dataKey : keys) {
                Map<Object, Object> crawledPages = redisTemplate.opsForHash().entries(dataKey);
                for (Map.Entry<Object, Object> entry : crawledPages.entrySet()) {
                    String url = (String) entry.getKey();
                    String content = (String) entry.getValue();

                    if (url == null || content == null) continue;
                    if (isHomepage(url)) continue;

                    String lowerContent = content.toLowerCase(Locale.ROOT);
                    if (lowerContent.contains(lowerQuery)) {
                        String snippet = buildFallbackSnippet(content, lowerQuery);
                        rawResults.add(new SearchResultDto(url, snippet));
                    }
                }
            }

            // 3. Deduplicate results by URL to prevent showing duplicates from different crawl runs
            Map<String, SearchResultDto> uniqueMap = new LinkedHashMap<>();
            for (SearchResultDto dto : rawResults) {
                if (dto.getUrl() != null) {
                    // keeps the first occurrence, maintaining insertion order of keys
                    uniqueMap.putIfAbsent(dto.getUrl(), dto);
                }
            }

            fallbackResults.addAll(uniqueMap.values());
            logger.info("Redis fallback search completed. Found {} unique matching pages (raw hits containing query: {}).", fallbackResults.size(), rawResults.size());
        } catch (Exception ex) {
            logger.error("Severe fallback error searching Redis data: {}", ex.getMessage(), ex);
        }
        return fallbackResults;
    }

    private String buildFallbackSnippet(String content, String query) {
        int index = content.toLowerCase(Locale.ROOT).indexOf(query);
        if (index == -1) return content.substring(0, Math.min(content.length(), 150)) + "...";

        int start = Math.max(0, index - 60);
        int end = Math.min(content.length(), index + query.length() + 80);

        String rawSnippet = content.substring(start, end);
        String matchedPart = content.substring(index, index + query.length());

        // Add exact simulated HTML em tag highlighting just like Elasticsearch outputs
        return "..." + rawSnippet.replace(matchedPart, "<em>" + matchedPart + "</em>") + "...";
    }

    private boolean isHomepage(String url) {
        try {
            java.net.URL u = new java.net.URL(url);
            String path = u.getPath();
            if (path == null) return true;
            path = path.trim();
            if (path.equals("") || path.equals("/")) return true;
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private boolean isLikelyArticle(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains("/article/") || lower.contains("/news/")) return true;
        if (lower.matches(".*\\b(docid|articleid|newsid|storyid|id)=\\d+.*")) return true;
        if (lower.matches(".*/\\d{4}/\\d{2}/\\d{2}/.*")) return true;
        if (lower.matches(".*/\\d{6,}.*")) return true;
        try {
            java.net.URL u = new java.net.URL(url);
            String path = u.getPath();
            if (path == null) return false;
            String[] parts = path.split("/");
            int count = 0;
            for (String p : parts) if (!p.isEmpty()) count++;
            return count >= 3;
        } catch (Exception e) {
            return false;
        }
    }

    private String extractSnippet(Map<String, Object> hit) {
        Map<String, Object> hl = (Map<String, Object>) hit.get("highlight");
        if (hl != null) {
            // Prefer highlighting from the title
            Object titleHighlight = hl.get("title");
            if (titleHighlight instanceof List && !((List) titleHighlight).isEmpty()) {
                return (String) ((List) titleHighlight).get(0);
            }
            // Fallback to content highlighting
            Object contentHighlight = hl.get("content");
            if (contentHighlight instanceof List && !((List) contentHighlight).isEmpty()) {
                return (String) ((List) contentHighlight).get(0);
            }
        }
        return "";
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}