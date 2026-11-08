package com.handson.searchengine.controller;

import com.handson.searchengine.crawler.Crawler;
import com.handson.searchengine.model.CrawlerRequest;
import com.handson.searchengine.model.CrawlStatusOut;
import com.handson.searchengine.model.SearchResultDto;
import com.handson.searchengine.util.ElasticSearch;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AppController {
    private static final int MAX_CRAWL_SECONDS = 120;
    private static final int MAX_CRAWL_DEPTH = 4;
    private static final int MAX_CRAWL_URLS = 100;
    private static final int MAX_URL_LENGTH = 2048;
    private static final int MAX_QUERY_LENGTH = 256;

    private final Crawler crawler;
    private final ElasticSearch elasticSearch;

    public AppController(Crawler crawler, ElasticSearch elasticSearch) {
        this.crawler = crawler;
        this.elasticSearch = elasticSearch;
    }

    @PostMapping("/crawl")
    public String crawl(@RequestBody CrawlerRequest request) throws IOException, InterruptedException {
        validate(request);
        String crawlId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        crawler.crawl(crawlId, request);
        return crawlId;
    }

    @GetMapping("/crawl/{crawlId}")
    public CrawlStatusOut getCrawl(@PathVariable String crawlId) {
        return crawler.getCrawlInfo(crawlId);
    }

    @PostMapping("/crawl/{crawlId}/stop")
    public void stopCrawl(@PathVariable String crawlId) {
        crawler.stopCrawlGracefully(crawlId);
    }

    @GetMapping("/search")
    public List<SearchResultDto> search(@RequestParam String query) {
        String term = query.strip();
        if (term.length() > MAX_QUERY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search query is too long");
        }
        return term.isEmpty() ? List.of() : elasticSearch.search(term);
    }

    @PostMapping("/sendKafka")
    public String sendKafka(@RequestBody CrawlerRequest request) throws IOException, InterruptedException {
        crawl(request);
        return "OK";
    }

    private void validate(CrawlerRequest request) {
        if (request == null || request.getUrl() == null || request.getUrl().trim().isEmpty()
                || request.getMaxSeconds() < 1 || request.getMaxSeconds() > MAX_CRAWL_SECONDS
                || request.getMaxDistance() < -1 || request.getMaxDistance() > MAX_CRAWL_DEPTH
                || request.getMaxUrls() == 0 || request.getMaxUrls() < -1
                || request.getMaxUrls() > MAX_CRAWL_URLS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid crawl parameters");
        }
        String url = request.getUrl().trim();
        if (!url.contains("://")) url = "https://" + url;
        try {
            URI uri = new URI(url).normalize();
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535) {
                throw new URISyntaxException(url, "Expected an HTTP or HTTPS URL");
            }
            String normalizedUrl = uri.toASCIIString().split("#", 2)[0];
            if (normalizedUrl.length() > MAX_URL_LENGTH) {
                throw new URISyntaxException(url, "Website URL is too long");
            }
            request.setUrl(normalizedUrl);
        } catch (URISyntaxException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid website URL");
        }
        if (request.getMaxDistance() == -1) request.setMaxDistance(CrawlerRequest.DEFAULT_DEPTH);
        if (request.getMaxUrls() == -1) request.setMaxUrls(CrawlerRequest.DEFAULT_URLS);
    }
}