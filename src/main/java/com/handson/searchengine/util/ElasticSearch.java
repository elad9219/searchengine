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
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

@Component
public class ElasticSearch {
    private static final Logger logger = LoggerFactory.getLogger(ElasticSearch.class);
    OkHttpClient client = new OkHttpClient();

    @Value("${elasticsearch.base.url}")
    private String ELASTIC_SEARCH_URL;

    @Value("${elasticsearch.key}")
    private String API_KEY;

    @Value("${elasticsearch.index}")
    private String index;

    // If true, add ?refresh=true to index requests (dev convenience). Set to false in production to avoid overhead.
    @Value("${elasticsearch.refresh:false}")
    private boolean forceRefresh;

    @Autowired
    ObjectMapper om;

    public void addData(UrlSearchDoc doc) throws IOException {
        String auth = new String(Base64.encodeBase64(API_KEY.getBytes()));
        String json = om.writeValueAsString(doc);

        RequestBody body = RequestBody.create(MediaType.parse("application/json"), json);

        String url = ELASTIC_SEARCH_URL + "/" + index + "/_doc";
        if (forceRefresh) url += "?refresh=true";

        Request request = new Request.Builder()
                .url(url)
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

    public List<SearchResultDto> search(String query) throws IOException {
        List<SearchResultDto> results = new ArrayList<>();
        String auth = new String(Base64.encodeBase64(API_KEY.getBytes()));

        String requestBody = "{\n" +
                "  \"size\": 100,\n" +
                "  \"query\": {\n" +
                "    \"match\": {\n" +
                "      \"content\": {\n" +
                "        \"query\": \"" + escapeJson(query) + "\",\n" +
                "        \"operator\": \"and\"\n" +
                "      }\n" +
                "    }\n" +
                "  },\n" +
                "  \"highlight\": {\n" +
                "    \"pre_tags\": [\"<em>\"],\n" +
                "    \"post_tags\": [\"</em>\"],\n" +
                "    \"fields\": { \"content\": {} }\n" +
                "  }\n" +
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
                logger.error("ElasticSearch search failed: code={} message={}", response.code(), response.message());
                return results;
            }
            String responseBody = response.body().string();
            Map<String, Object> responseMap = om.readValue(responseBody, Map.class);
            Map<String, Object> hitsWrap = (Map<String, Object>) responseMap.get("hits");
            if (hitsWrap == null) return results;
            List<Map<String, Object>> hits = (List<Map<String, Object>>) hitsWrap.get("hits");
            if (hits == null) return results;

            // buckets & dedupe
            List<SearchResultDto> withSnippet = new ArrayList<>();
            List<SearchResultDto> deepPaths = new ArrayList<>();
            List<SearchResultDto> fallback = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            for (Map<String, Object> hit : hits) {
                Map<String, Object> source = (Map<String, Object>) hit.get("_source");
                if (source == null) continue;
                String url = (String) source.get("url");
                if (url == null) continue;
                if (seen.contains(url)) continue;

                String snippet = extractSnippet(hit);
                boolean hasSnippet = snippet != null && snippet.trim().length() > 0;
                int depth = pathDepth(url);
                boolean articleLike = looksLikeArticle(url);

                if (hasSnippet) {
                    withSnippet.add(new SearchResultDto(url, snippet));
                    seen.add(url);
                    continue;
                }
                if (articleLike || depth >= 2) {
                    deepPaths.add(new SearchResultDto(url, snippet));
                    seen.add(url);
                    continue;
                }
                fallback.add(new SearchResultDto(url, snippet));
                seen.add(url);
            }

            for (SearchResultDto s : withSnippet) { if (results.size() >= 50) break; results.add(s); }
            for (SearchResultDto s : deepPaths) { if (results.size() >= 50) break; results.add(s); }
            for (SearchResultDto s : fallback) { if (results.size() >= 50) break; results.add(s); }
        }

        return results;
    }

    private boolean looksLikeArticle(String url) {
        String lower = url.toLowerCase();
        return lower.contains("/news/") || lower.contains("/article/") || lower.contains("/articles") ||
                lower.contains(".html") || lower.matches(".*/\\d+.*");
    }

    private int pathDepth(String url) {
        try {
            java.net.URL u = new java.net.URL(url);
            String p = u.getPath();
            if (p == null || p.equals("") || p.equals("/")) return 0;
            String[] parts = p.split("/");
            int depth = 0;
            for (String part : parts) if (!part.isEmpty()) depth++;
            return depth;
        } catch (Exception e) {
            return 0;
        }
    }

    private String extractSnippet(Map<String, Object> hit) {
        Map<String, Object> highlight = (Map<String, Object>) hit.get("highlight");
        if (highlight != null) {
            Object cont = highlight.get("content");
            if (cont instanceof List && !((List) cont).isEmpty()) {
                return (String) ((List) cont).get(0);
            } else if (cont != null) {
                return cont.toString();
            }
        }
        return "";
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
