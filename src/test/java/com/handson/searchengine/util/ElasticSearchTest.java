package com.handson.searchengine.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.handson.searchengine.model.SearchResultDto;
import com.handson.searchengine.model.UrlSearchDoc;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticSearchTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private HttpServer server;
    private ElasticSearch search;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        search = new ElasticSearch(mapper, "http://127.0.0.1:" + server.getAddress().getPort(),
                "local:fixture", "pages");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void serializesControlCharactersAndReturnsAnEmptyResult() throws IOException {
        respond("_search", 200, """
                {"hits":{"hits":[]}}
                """);
        String query = "some\tquery\rwith \"quotes\" and a \\ slash";
        assertTrue(search.search(query).isEmpty());
        assertEquals(query, mapper.readTree(requestBody.get())
                .path("query").path("bool").path("must").path(0).path("multi_match").path("query").asText());
    }

    @Test
    void missingIndexCanRepresentAFirstRun() {
        respond("_search", 404, """
                {"error":{"type":"index_not_found_exception"}}
                """);
        assertTrue(search.search("Andorra").isEmpty());
    }

    @Test
    void serviceFailureIsNotAnEmptySearchResult() {
        respond("_search", 503, "{}");
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> search.search("Andorra")).getStatusCode().value());
    }

    @Test
    void partialSearchIsReportedAsUnavailable() {
        respond("_search", 200, """
                {"timed_out":true,"hits":{"hits":[]}}
                """);
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> search.search("Andorra")).getStatusCode().value());
    }

    @Test
    void reportsRejectedIndexWrites() {
        respond("_doc", 403, "{}");
        UrlSearchDoc document = UrlSearchDoc.of("fixture", "content", "https://example.com/page",
                "https://example.com", 0, "html");
        assertThrows(IOException.class, () -> search.addData(document));
    }

    @Test
    void keepsArticlePriorityAndCapsResults() {
        var body = mapper.createObjectNode();
        ArrayNode hits = body.putObject("hits").putArray("hits");
        hit(hits, "https://example.com/about");
        hit(hits, "https://example.com/");
        hit(hits, "https://example.com/news/0");
        for (int i = 0; i < 55; i++) {
            hit(hits, "https://example.com/news/" + i);
        }
        respond("_search", 200, body.toString());

        List<SearchResultDto> results = search.search("Andorra");
        assertEquals(50, results.size());
        assertEquals("https://example.com/news/0", results.getFirst().getUrl());
        assertEquals("<em>Andorra</em>", results.getFirst().getSnippet());
        assertEquals("https://example.com/news/49", results.getLast().getUrl());
    }

    private void hit(ArrayNode hits, String url) {
        var hit = hits.addObject();
        hit.putObject("_source").put("url", url);
        hit.putObject("highlight").putArray("content").add("<em>Andorra</em>");
    }

    private void respond(String operation, int status, String response) {
        server.createContext("/pages/" + operation, exchange -> {
            try (exchange) {
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] body = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
            }
        });
    }
}