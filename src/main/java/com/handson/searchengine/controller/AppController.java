package com.handson.searchengine.controller;

import com.handson.searchengine.model.CrawlStatusOut;
import com.handson.searchengine.model.CrawlerRequest;
import com.handson.searchengine.model.SearchResultDto;
import com.handson.searchengine.util.ElasticSearch;
import com.handson.searchengine.crawler.Crawler;
import com.handson.searchengine.kafka.Producer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Random;

@RestController
@RequestMapping("/api")
public class AppController {

    private static final int ID_LENGTH = 6;
    private Random random = new Random();

    @Autowired
    Crawler crawler;

    @Autowired
    Producer producer;

    @Autowired
    ElasticSearch elasticSearch;

    // Start a crawl: returns crawlId (string) - unchanged contract for frontend compatibility
    @PostMapping("/crawl")
    public String crawl(@RequestBody CrawlerRequest request) throws IOException, InterruptedException {
        String crawlId = generateCrawlId();

        // Normalize URL: ensure protocol and WWW
        String u = request.getUrl();
        if (u == null) u = "";
        u = u.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "https://" + u;
        }
        try {
            URL parsed = new URL(u);
            String host = parsed.getHost();
            if (!host.startsWith("www.")) {
                String newHost = "www." + host;
                StringBuilder rebuilt = new StringBuilder();
                rebuilt.append(parsed.getProtocol()).append("://").append(newHost);
                if (parsed.getPort() != -1) rebuilt.append(":").append(parsed.getPort());
                String path = parsed.getPath();
                if (path != null) rebuilt.append(path);
                if (parsed.getQuery() != null) rebuilt.append("?").append(parsed.getQuery());
                u = rebuilt.toString();
            }
        } catch (Exception e) {
            // if parsing fails, keep best-effort url
        }
        request.setUrl(u);

        // run crawler in background (existing behavior)
        new Thread(() -> {
            try {
                crawler.crawl(crawlId, request);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        return crawlId;
    }

    // Get crawl status (returns CrawlStatusOut with millis fields)
    @GetMapping("/crawl/{crawlId}")
    public CrawlStatusOut getCrawl(@PathVariable String crawlId) throws IOException, InterruptedException {
        return crawler.getCrawlInfo(crawlId);
    }

    // Search endpoint: returns list of url + snippet (highlight) DTOs
    @GetMapping("/search")
    public List<SearchResultDto> search(@RequestParam String query) throws IOException {
        return elasticSearch.search(query);
    }

    // send direct kafka payload (kept for testing) - also normalize URL
    @PostMapping("/sendKafka")
    public String sendKafka(@RequestBody CrawlerRequest request) throws IOException, InterruptedException {
        String u = request.getUrl();
        if (u == null) u = "";
        u = u.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "https://" + u;
        }
        try {
            URL parsed = new URL(u);
            String host = parsed.getHost();
            if (!host.startsWith("www.")) {
                String newHost = "www." + host;
                StringBuilder rebuilt = new StringBuilder();
                rebuilt.append(parsed.getProtocol()).append("://").append(newHost);
                if (parsed.getPort() != -1) rebuilt.append(":").append(parsed.getPort());
                String path = parsed.getPath();
                if (path != null) rebuilt.append(path);
                if (parsed.getQuery() != null) rebuilt.append("?").append(parsed.getQuery());
                u = rebuilt.toString();
            }
        } catch (Exception e) { /* ignore */ }
        request.setUrl(u);

        producer.send(request);
        return "OK";
    }

    private String generateCrawlId() {
        String charPool = "ABCDEFHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder res = new StringBuilder();
        for (int i = 0; i < ID_LENGTH; i++) {
            res.append(charPool.charAt(random.nextInt(charPool.length())));
        }
        return res.toString();
    }
}
