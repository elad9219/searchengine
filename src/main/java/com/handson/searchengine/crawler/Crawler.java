package com.handson.searchengine.crawler;

import com.handson.searchengine.kafka.Producer;
import com.handson.searchengine.model.CrawlStatus;
import com.handson.searchengine.model.CrawlStatusOut;
import com.handson.searchengine.model.CrawlerRecord;
import com.handson.searchengine.model.CrawlerRequest;
import com.handson.searchengine.model.UrlSearchDoc;
import com.handson.searchengine.util.ElasticSearch;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class Crawler {
    private static final Logger logger = LoggerFactory.getLogger(Crawler.class);
    private final CrawlStateStore states;
    private final StringRedisTemplate redis;
    private final Producer producer;
    private final ElasticSearch elasticSearch;

    public Crawler(CrawlStateStore states, StringRedisTemplate redis,
                   Producer producer, ElasticSearch elasticSearch) {
        this.states = states;
        this.redis = redis;
        this.producer = producer;
        this.elasticSearch = elasticSearch;
    }

    public void crawl(String crawlId, CrawlerRequest request) throws IOException, InterruptedException {
        CrawlerRecord record = CrawlerRecord.of(request).withCrawlId(crawlId);
        states.initialize(record);
        try {
            if (states.queue(record)) producer.send(record);
        } catch (IOException | InterruptedException | RuntimeException e) {
            states.fail(crawlId, "Could not confirm submission to Kafka");
            throw e;
        }
    }

    public void crawlOneRecord(String crawlId, CrawlerRecord record) {
        if (record == null || crawlId == null || !crawlId.equals(record.getCrawlId())) {
            throw new IllegalArgumentException("Invalid crawl record");
        }
        if (!states.claim(record)) return;

        try {
            Document page = fetch(record);
            if (!isRunning(crawlId)) return;
            String content = page.body() == null ? "" : page.body().text();
            if (content.trim().isEmpty()) throw new IOException("Page has no readable content");

            String backupKey = crawlId + ".data";
            redis.opsForHash().put(backupKey, record.getUrl(), content);
            redis.expire(backupKey, Duration.ofHours(24));

            try {
                elasticSearch.addData(UrlSearchDoc.of(crawlId, content, record.getUrl(),
                        record.getBaseUrl(), record.getDistance(), "html"));
            } catch (IOException e) {
                logger.warn("Index request failed; Redis backup retained for crawl {}", crawlId);
            }

            if (!isRunning(crawlId)) return;
            for (String url : links(record.getBaseUrl(), page)) {
                CrawlerRecord child = CrawlerRecord.of(record).withUrl(url).withIncDistance();
                if (states.queue(child)) producer.send(child);
            }
            states.finish(record);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            states.fail(crawlId, "Crawl processing was interrupted");
        } catch (Exception e) {
            logger.warn("Crawl processing failed for ID {}", crawlId, e);
            states.fail(crawlId, "Could not finish processing a page");
        }
    }

    Document fetch(CrawlerRecord record) throws IOException {
        long remaining = record.getMaxTime() - System.currentTimeMillis();
        if (remaining <= 0) throw new IOException("Crawl deadline reached");
        return Jsoup.connect(record.getUrl())
                .userAgent("PortfolioSearchEngine/1.0")
                .timeout((int) Math.min(15000, remaining))
                .maxBodySize(2 * 1024 * 1024)
                .followRedirects(true)
                .get();
    }

    private List<String> links(String baseUrl, Document page) {
        return page.select("a[href]").eachAttr("abs:href").stream()
                .filter(url -> sameOrigin(baseUrl, url))
                .map(url -> url.split("#", 2)[0])
                .distinct()
                .collect(Collectors.toList());
    }

    private boolean sameOrigin(String baseUrl, String url) {
        try {
            URI base = URI.create(baseUrl);
            URI target = URI.create(url);
            return base.getScheme().equalsIgnoreCase(target.getScheme())
                    && base.getHost().equalsIgnoreCase(target.getHost())
                    && port(base) == port(target)
                    && target.getUserInfo() == null;
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }

    private int port(URI uri) {
        return uri.getPort() >= 0 ? uri.getPort()
                : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
    }

    private boolean isRunning(String crawlId) {
        CrawlStatus status = states.status(crawlId);
        return status != null && status.getStopReason() == null;
    }

    public CrawlStatusOut getCrawlInfo(String crawlId) {
        CrawlStatus status = states.status(crawlId);
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Crawl not found or expired");
        }
        return CrawlStatusOut.of(status);
    }

    public void stopCrawlGracefully(String crawlId) {
        stopCrawlGracefully(crawlId, "Manually stopped by user");
    }

    public void stopCrawlGracefully(String crawlId, String message) {
        getCrawlInfo(crawlId);
        states.stop(crawlId, message);
    }

    public void updateCrawlStatusWithError(String crawlId, String message) {
        states.fail(crawlId, message);
    }
}