package com.handson.searchengine.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.handson.searchengine.model.SearchResultDto;
import com.handson.searchengine.model.UrlSearchDoc;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Component
public class ElasticSearch {
    private static final Logger logger = LoggerFactory.getLogger(ElasticSearch.class);
    private static final MediaType JSON = MediaType.get("application/json");

    private final OkHttpClient client = new OkHttpClient.Builder()
            .callTimeout(3, TimeUnit.SECONDS)
            .build();
    private final ObjectMapper mapper;
    private final String indexUrl;
    private final String authorization;

    public ElasticSearch(ObjectMapper mapper,
                         @Value("${elasticsearch.base.url}") String baseUrl,
                         @Value("${elasticsearch.key}") String apiKey,
                         @Value("${elasticsearch.index}") String index) {
        this.mapper = mapper;
        this.indexUrl = baseUrl + "/" + index;
        this.authorization = "Basic " + Base64.getEncoder()
                .encodeToString(apiKey.getBytes(StandardCharsets.UTF_8));
    }

    public void addData(UrlSearchDoc doc) throws IOException {
        try (Response response = post("_doc", doc)) {
            if (!response.isSuccessful()) {
                throw new IOException("Elasticsearch indexing returned HTTP " + response.code());
            }
        }
    }

    public List<SearchResultDto> search(String query) {
        Map<String, Object> body = Map.of(
                "size", 80,
                "query", Map.of("bool", Map.of("must", List.of(
                        Map.of("multi_match", Map.of("query", query,
                                "fields", List.of("title^3", "content")))))),
                "highlight", Map.of(
                        "pre_tags", List.of("<em>"), "post_tags", List.of("</em>"),
                        "fields", Map.of("content", Map.of(), "title", Map.of())));

        try (Response response = post("_search", body)) {
            if (!response.isSuccessful() && response.code() != 404) {
                throw new IOException("Elasticsearch search returned HTTP " + response.code());
            }
            if (response.body() == null) throw new IOException("Missing Elasticsearch response body");
            JsonNode result = mapper.readTree(response.body().byteStream());
            if (result == null) throw new IOException("Empty Elasticsearch response body");
            if (response.code() == 404 && "index_not_found_exception".equals(
                    result.path("error").path("type").asText())) {
                return List.of();
            }

            JsonNode hits = result.path("hits").path("hits");
            if (!response.isSuccessful() || !hits.isArray()
                    || result.path("timed_out").asBoolean()
                    || result.path("_shards").path("failed").asInt() > 0) {
                throw new IOException("Elasticsearch did not return a complete search result");
            }
            return searchResults(hits);
        } catch (IOException e) {
            logger.warn("Elasticsearch search failed: {}", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Search is temporarily unavailable", e);
        }
    }

    private Response post(String operation, Object body) throws IOException {
        Request request = new Request.Builder()
                .url(indexUrl + "/" + operation)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .post(RequestBody.create(JSON, mapper.writeValueAsBytes(body)))
                .build();
        return client.newCall(request).execute();
    }

    private List<SearchResultDto> searchResults(JsonNode hits) {
        Set<String> seen = new HashSet<>();
        List<SearchResultDto> articles = new ArrayList<>();
        List<SearchResultDto> others = new ArrayList<>();

        for (JsonNode hit : hits) {
            String url = hit.path("_source").path("url").asText("");
            String path;
            try {
                URI uri = URI.create(url);
                if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                    continue;
                }
                path = uri.getRawPath();
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (path == null || path.isBlank() || path.equals("/") || !seen.add(url)) continue;

            JsonNode highlight = hit.path("highlight");
            String snippet = highlight.path("title").path(0)
                    .asText(highlight.path("content").path(0).asText(""));
            SearchResultDto dto = new SearchResultDto(url, snippet);
            (isLikelyArticle(url, path) ? articles : others).add(dto);
        }
        return Stream.concat(articles.stream(), others.stream()).limit(50).toList();
    }

    private boolean isLikelyArticle(String url, String path) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains("/article/") || lower.contains("/news/")) return true;
        if (lower.matches(".*\\b(docid|articleid|newsid|storyid|id)=\\d+.*")) return true;
        if (lower.matches(".*/\\d{4}/\\d{2}/\\d{2}/.*")) return true;
        if (lower.matches(".*/\\d{6,}.*")) return true;

        int count = 0;
        for (String part : path.split("/")) {
            if (!part.isEmpty()) count++;
        }
        return count >= 3;
    }
}