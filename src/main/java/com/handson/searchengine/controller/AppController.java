package com.handson.searchengine.controller;

import com.handson.searchengine.crawler.Crawler;
import com.handson.searchengine.model.CrawlStatusOut;
import com.handson.searchengine.model.CrawlerRequest;
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
    public List<SearchResultDto> search(@RequestParam String query) throws IOException {
        if (query.trim().isEmpty()) return java.util.Collections.emptyList();
        return elasticSearch.search(query.trim());
    }

    @PostMapping("/sendKafka")
    public String sendKafka(@RequestBody CrawlerRequest request) throws IOException, InterruptedException {
        crawl(request);
        return "OK";
    }

    private void validate(CrawlerRequest request) {
        if (request == null || request.getUrl() == null || request.getUrl().trim().isEmpty()
                || request.getMaxSeconds() < 1 || request.getMaxSeconds() > 9999
                || request.getMaxDistance() < -1
                || request.getMaxUrls() == 0 || request.getMaxUrls() < -1) {
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
            request.setUrl(uri.toASCIIString().split("#", 2)[0]);
        } catch (URISyntaxException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid website URL");
        }
    }
}