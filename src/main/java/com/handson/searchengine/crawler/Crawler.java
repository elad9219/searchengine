package com.handson.searchengine.crawler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.handson.searchengine.kafka.Producer;
import com.handson.searchengine.model.*;
import com.handson.searchengine.util.ElasticSearch;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class Crawler {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private Producer producer;

    @Autowired
    private ElasticSearch elasticSearch;

    protected final Log logger = LogFactory.getLog(getClass());

    private final ExecutorService indexExecutor = Executors.newFixedThreadPool(4);

    public void crawl(String crawlId, CrawlerRequest crawlerRequest) throws InterruptedException, IOException, JsonProcessingException {
        logger.info("Starting crawl with ID: " + crawlId);
        initCrawlInRedis(crawlId);
        CrawlerRecord first = CrawlerRecord.of(crawlerRequest).withCrawlId(crawlId);
        producer.send(first);
        logger.info("Sent initial record for crawl ID: " + crawlId);
    }

    public void crawlOneRecord(String crawlId, CrawlerRecord rec) {
        logger.info("Consumer processing crawl for URL: " + rec.getUrl() + " with crawlId: " + crawlId);

        try {
            StopReason stopReason = getStopReason(rec);
            CrawlStatus current = readStatus(crawlId);
            long startTime = current != null ? current.getStartTimeMillis() : System.currentTimeMillis();

            try {
                setCrawlStatus(crawlId, CrawlStatus.of(rec.getDistance(), startTime, getVisitedUrls(crawlId), stopReason));
            } catch (JsonProcessingException e) {
                logger.warn("Failed to set crawl status at start: " + e.getMessage());
            }

            if (stopReason != null) {
                logger.debug("Not crawling " + rec.getUrl() + " because stopReason=" + stopReason);
                return;
            }

            if (!isUrlAccessible(rec.getUrl())) {
                updateCrawlStatusWithError(crawlId, "URL is not accessible or blocked by robots.txt: " + rec.getUrl());
                return;
            }

            Document webPageContent = fetchWithRetry(rec.getUrl(), 3); // 3 retries

            if (webPageContent != null) {
                indexElasticSearchAsync(rec, webPageContent);
                List<String> innerUrls = extractWebPageUrls(rec.getBaseUrl(), webPageContent);
                addUrlsToQueue(rec, innerUrls, rec.getDistance() + 1);
                logger.info("Successfully crawled: " + rec.getUrl() + " with " + innerUrls.size() + " new URLs");
            }

        } catch (Exception e) {
            String errorMsg = "Failed to crawl " + rec.getUrl() + ": " + e.getMessage();
            logger.error(errorMsg, e);
            updateCrawlStatusWithError(crawlId, errorMsg);
        }
    }

    private Document fetchWithRetry(String url, int maxRetries) throws IOException {
        int attempts = 0;
        IOException lastException = null;

        while (attempts < maxRetries) {
            try {
                return Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                        .header("Accept-Language", "en-US,en;q=0.5")
                        .header("Connection", "keep-alive")
                        .header("Upgrade-Insecure-Requests", "1")
                        .timeout(60_000) // Increased to 60 seconds
                        .followRedirects(true)
                        .maxBodySize(10 * 1024 * 1024)
                        .get();
            } catch (IOException e) {
                lastException = e;
                attempts++;
                logger.warn("Attempt " + attempts + "/" + maxRetries + " failed for " + url + ": " + e.getMessage());

                if (attempts >= maxRetries) {
                    break;
                }

                try {
                    long delay = (long) Math.pow(2, attempts - 1) * 1000;
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Crawler interrupted", ie);
                }
            }
        }

        throw lastException != null ? lastException : new IOException("Unknown error occurred");
    }

    private boolean isUrlAccessible(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; SimpleCrawler/1.0)")
                    .timeout(10_000)
                    .followRedirects(true)
                    .execute()
                    .statusCode() < 400;
        } catch (Exception e) {
            logger.debug("URL check failed for " + url + ": " + e.getMessage());
            return false;
        }
    }

    private void updateCrawlStatusWithError(String crawlId, String errorMessage) {
        try {
            CrawlStatus current = readStatus(crawlId);
            if (current != null) {
                current.setErrorMessage(errorMessage);
                setCrawlStatus(crawlId, current);
                logger.error("Crawl error for " + crawlId + ": " + errorMessage);
            }
        } catch (Exception e) {
            logger.error("Failed to update crawl status with error", e);
        }
    }

    private StopReason getStopReason(CrawlerRecord rec) {
        if (rec.getDistance() == rec.getMaxDistance() + 1) return StopReason.maxDistance;
        if (getVisitedUrls(rec.getCrawlId()) >= rec.getMaxUrls()) return StopReason.maxUrls;
        if (System.currentTimeMillis() >= rec.getMaxTime()) return StopReason.timeout;
        return null;
    }

    private void addUrlsToQueue(CrawlerRecord rec, List<String> urls, int distance) throws InterruptedException, JsonProcessingException {
        logger.info("Adding URLs to queue: distance->" + distance + " amount->" + urls.size());
        int currentVisited = getVisitedUrls(rec.getCrawlId());
        int remainingSlots = rec.getMaxUrls() - currentVisited;
        if (remainingSlots <= 0 || System.currentTimeMillis() >= rec.getMaxTime()) return;
        List<String> urlsToAdd = urls.stream().limit(remainingSlots).collect(Collectors.toList());
        for (String url : urlsToAdd) {
            if (System.currentTimeMillis() >= rec.getMaxTime()) break;
            if (!crawlHasVisited(rec, url)) {
                producer.send(CrawlerRecord.of(rec).withUrl(url).withIncDistance());
            }
        }
    }

    private List<String> extractWebPageUrls(String baseUrl, Document webPageContent) {
        List<String> links = webPageContent.select("a[href]")
                .eachAttr("abs:href")
                .stream()
                .filter(url -> url != null)
                .filter(url -> url.startsWith(baseUrl))
                .filter(url -> !url.startsWith("mailto:"))
                .filter(url -> !url.startsWith("javascript:"))
                .distinct()
                .collect(Collectors.toList());
        logger.info("Extracted " + links.size() + " unique links");
        return links;
    }

    private void indexElasticSearchAsync(CrawlerRecord rec, Document webPageContent) {
        logger.info("Scheduling Elasticsearch index for: " + rec.getUrl());
        String text = webPageContent.body() != null ? webPageContent.body().text() : "";
        UrlSearchDoc searchDoc = UrlSearchDoc.of(rec.getCrawlId(), text, rec.getUrl(), rec.getBaseUrl(), rec.getDistance(), "html");
        indexExecutor.submit(() -> {
            try {
                elasticSearch.addData(searchDoc);
                logger.info("Indexed URL successfully: " + rec.getUrl());
            } catch (Exception e) {
                logger.error("Failed to index doc async: " + rec.getUrl() + " - " + e.getMessage(), e);
            }
        });
    }

    private void initCrawlInRedis(String crawlId) throws JsonProcessingException {
        long now = System.currentTimeMillis();
        setCrawlStatus(crawlId, CrawlStatus.of(0, now, 0, null));
        redisTemplate.opsForValue().set(crawlId + ".urls.count", "0");
        redisTemplate.opsForSet().add(crawlId + ".visited", crawlId); // Initial entry
        logger.info("Initialized crawl in Redis with ID: " + crawlId);
    }

    private void setCrawlStatus(String crawlId, CrawlStatus crawlStatus) throws JsonProcessingException {
        crawlStatus.setLastModifiedMillis(System.currentTimeMillis());
        redisTemplate.opsForValue().set(crawlId + ".status", om.writeValueAsString(crawlStatus));
        logger.debug("Set crawl status for ID: " + crawlId);
    }

    private CrawlStatus readStatus(String crawlId) {
        try {
            Object statusObj = redisTemplate.opsForValue().get(crawlId + ".status");
            if (statusObj == null) {
                logger.warn("No status found for crawlId: " + crawlId);
                return null;
            }
            return om.readValue(statusObj.toString(), CrawlStatus.class);
        } catch (Exception e) {
            logger.error("Failed reading crawl status for " + crawlId + ": " + e.getMessage(), e);
            return null;
        }
    }

    private boolean crawlHasVisited(CrawlerRecord rec, String url) {
        @SuppressWarnings("unchecked")
        Boolean isMember = redisTemplate.opsForSet().isMember(rec.getCrawlId() + ".visited", url);
        if (isMember == null || !isMember) {
            redisTemplate.opsForSet().add(rec.getCrawlId() + ".visited", url);
            redisTemplate.opsForValue().increment(rec.getCrawlId() + ".urls.count", 1L);
            try {
                CrawlStatus cur = readStatus(rec.getCrawlId());
                long startTime = cur != null ? cur.getStartTimeMillis() : rec.getStartTime();
                int newCount = getVisitedUrls(rec.getCrawlId());
                setCrawlStatus(rec.getCrawlId(), CrawlStatus.of(rec.getDistance(), startTime, newCount, null));
            } catch (JsonProcessingException e) {
                logger.warn("Failed to update crawl status after visited URL: " + e.getMessage());
            }
            return false;
        } else {
            return true;
        }
    }

    private int getVisitedUrls(String crawlId) {
        Object curCount = redisTemplate.opsForValue().get(crawlId + ".urls.count");
        if (curCount == null) return 0;
        try {
            return Integer.parseInt(curCount.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public CrawlStatusOut getCrawlInfo(String crawlId) {
        try {
            Object statusObj = redisTemplate.opsForValue().get(crawlId + ".status");
            if (statusObj == null) {
                logger.warn("No status found for crawlId: " + crawlId);
                long now = System.currentTimeMillis();
                return CrawlStatusOut.of(CrawlStatus.of(0, now, 0, null));
            }
            CrawlStatus cs = om.readValue(statusObj.toString(), CrawlStatus.class);
            cs.setNumPages(getVisitedUrls(crawlId));
            return CrawlStatusOut.of(cs);
        } catch (Exception e) {
            logger.error("Failed reading crawl status for " + crawlId + ": " + e.getMessage(), e);
            long now = System.currentTimeMillis();
            return CrawlStatusOut.of(CrawlStatus.of(0, now, 0, null));
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            indexExecutor.shutdownNow();
            logger.info("Shutting down crawler executor service");
        } catch (Exception ignore) {}
    }
}