package com.handson.searchengine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.config.location=optional:classpath:/searchengine-test.properties",
		"spring.kafka.bootstrap-servers=127.0.0.1:1",
		"spring.kafka.listener.auto-startup=false",
		"spring.data.redis.host=127.0.0.1",
		"spring.data.redis.port=1",
		"management.health.redis.enabled=false",
		"management.endpoints.web.cors.allowed-origins=http://localhost:3000",
		"management.endpoints.web.cors.allowed-methods=GET",
		"spring.http.converters.preferred-json-mapper=jackson2",
		"elasticsearch.base.url=http://127.0.0.1:1",
		"elasticsearch.key=test:test",
		"elasticsearch.index=searchengine-test"
})
class SearchengineApplicationTests {
	private final HttpClient client = HttpClient.newHttpClient();

	@LocalServerPort
	private int port;

	@Autowired
	private ObjectMapper mapper;

	@Test
	void healthIsReadableFromTheFrontendOrigin() throws Exception {
		HttpResponse<String> response = get("/actuator/health");
		assertEquals(200, response.statusCode());
		assertEquals("http://localhost:3000",
				response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
		assertEquals("UP", mapper.readTree(response.body()).path("status").asText());
	}

	@Test
	void openApiDescribesTheExistingCrawlAndSearchEndpoints() throws Exception {
		HttpResponse<String> response = get("/v3/api-docs");
		assertEquals(200, response.statusCode());
		JsonNode document = mapper.readTree(response.body());
		assertTrue(document.path("openapi").asText().startsWith("3."));
		assertTrue(document.path("paths").path("/api/crawl").has("post"));
		assertTrue(document.path("paths").path("/api/search").has("get"));
	}

	@Test
	void emptySearchKeepsTheJsonArrayResponse() throws Exception {
		HttpResponse<String> response = get("/api/search?query=%20");
		assertEquals(200, response.statusCode());
		assertEquals(mapper.createArrayNode(), mapper.readTree(response.body()));
	}

	private HttpResponse<String> get(String path) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
				.header("Origin", "http://localhost:3000")
				.header("Accept", "application/json")
				.timeout(Duration.ofSeconds(10))
				.GET()
				.build();
		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}
}