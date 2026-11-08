package com.handson.searchengine.crawler;

import com.handson.searchengine.kafka.Producer;
import com.handson.searchengine.model.CrawlStatus;
import com.handson.searchengine.model.CrawlStatusOut;
import com.handson.searchengine.model.CrawlerRecord;
import com.handson.searchengine.model.CrawlerRequest;
import com.handson.searchengine.model.UrlSearchDoc;
import com.handson.searchengine.util.ElasticSearch;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.util.List;

@Service
public class Crawler {
    private static final Logger logger = LoggerFactory.getLogger(Crawler.class);
    private static final int MAX_LINK_CANDIDATES_PER_PAGE = 100;
    private final PublicPageFetcher pageFetcher = new PublicPageFetcher();
    private final CrawlStateStore states;
    private final Producer producer;
    private final ElasticSearch elasticSearch;

    public Crawler(CrawlStateStore states, Producer producer, ElasticSearch elasticSearch) {
        this.states = states;
        this.producer = producer;
        this.elasticSearch = elasticSearch;
    }

    public void crawl(String crawlId, CrawlerRequest request) throws IOException, InterruptedException {
        if (!states.admit()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Crawl start limit reached");
        }
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
            boolean root = record.getDistance() == 0;
            Document page;
            String content;
            try {
                page = fetch(record);
                if (!root && !sameOrigin(record.getBaseUrl(), page.location())) {
                    throw new IOException("Child page redirected outside the crawl origin");
                }
                content = page.body() == null ? "" : page.body().text();
                if (content.isBlank()) throw new IOException("Page has no readable content");
            } catch (IOException e) {
                if (root) throw e;
                states.skip(record);
                return;
            }

            if (!isRunning(crawlId)) return;
            String scope = root ? page.location() : record.getBaseUrl();
            elasticSearch.addData(UrlSearchDoc.of(crawlId, content, record.getUrl(),
                    scope, record.getDistance(), "html"));

            if (!isRunning(crawlId)) return;
            for (String url : links(scope, page)) {
                CrawlerRecord child = CrawlerRecord.of(record).withBaseUrl(scope)
                        .withUrl(url).withIncDistance();
                if (states.queue(child)) producer.send(child);
            }
            states.finish(record);
        } catch (PublicPageFetcher.RejectedTargetException e) {
            states.fail(crawlId, e.getMessage());
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
        return pageFetcher.fetch(record.getUrl(), Math.min(15000, remaining));
    }

    private List<String> links(String baseUrl, Document page) {
        String currentPage = withoutFragment(page.location());
        return page.selectStream("a[href]")
                .map(link -> withoutFragment(link.absUrl("href")))
                .filter(url -> url.length() <= 2048 && !url.equals(currentPage) && sameOrigin(baseUrl, url))
                .distinct()
                .limit(MAX_LINK_CANDIDATES_PER_PAGE)
                .toList();
    }

    private String withoutFragment(String url) {
        int fragment = url.indexOf('#');
        return fragment < 0 ? url : url.substring(0, fragment);
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