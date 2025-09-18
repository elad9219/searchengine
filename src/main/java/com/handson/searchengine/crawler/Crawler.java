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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        isShuttingDown.set(false); // Ensure clean state on startup
    }

    public void crawl(String crawlId, CrawlerRequest crawlerRequest) throws InterruptedException, IOException, JsonProcessingException {
        logger.info("Starting crawl with ID: " + crawlId + " at " + new java.util.Date());
        initCrawlInRedis(crawlId);
        CrawlerRecord first = CrawlerRecord.of(crawlerRequest).withCrawlId(crawlId);
        if (!isUrlValid(first.getUrl())) {
            updateCrawlStatusWithError(crawlId, "Invalid URL format: " + first.getUrl());
            return;
        }
        logger.info("Preparing to send initial record for URL: " + first.getUrl() + " at " + new java.util.Date());
        producer.send(first);
        logger.info("Sent initial record for crawl ID: " + crawlId + " at " + new java.util.Date());
    }

    @PostMapping("/stop/{crawlId}")
    public void stopCrawlGracefully(@PathVariable String crawlId) {
        stopCrawlGracefully(crawlId, "Manually stopped by user");
    }

    public void crawlOneRecord(String crawlId, CrawlerRecord rec) {
        logger.info("Consumer processing crawl for URL: " + rec.getUrl() + " with crawlId: " + crawlId + " at " + new java.util.Date());

        if (isShuttingDown.get()) {
            logger.info("Skipping crawl for " + rec.getUrl() + " due to shutdown request at " + new java.util.Date());
            return;
        }

        try {
            StopReason stopReason = getStopReason(rec);
            CrawlStatus current = readStatus(crawlId);
            long startTime = current != null ? current.getStartTimeMillis() : System.currentTimeMillis();

            try {
                setCrawlStatus(crawlId, CrawlStatus.of(rec.getDistance(), startTime, getVisitedUrls(crawlId), stopReason));
            } catch (JsonProcessingException e) {
                logger.warn("Failed to set crawl status at start: " + e.getMessage() + " at " + new java.util.Date());
            }

            if (stopReason != null) {
                logger.debug("Not crawling " + rec.getUrl() + " because stopReason=" + stopReason + " at " + new java.util.Date());
                return;
            }

            if (!isUrlAccessible(rec.getUrl())) {
                updateCrawlStatusWithError(crawlId, getAccessibilityErrorMessage(rec.getUrl()));
                return;
            }

            // Update visited before fetch to prevent loops
            if (!crawlHasVisited(rec, rec.getUrl())) {
                logger.info("Marked " + rec.getUrl() + " as visited at " + new java.util.Date());
            }

            Document webPageContent = fetchWithRetry(rec.getUrl(), 3);

            if (webPageContent != null) {
                // Improved content check
                String textContent = webPageContent.body() != null ? webPageContent.body().text().trim() : "";
                List<String> innerUrls = extractWebPageUrls(rec.getBaseUrl(), webPageContent);
                if (textContent.length() < 10 && innerUrls.isEmpty()) {
                    updateCrawlStatusWithError(crawlId, "Page contains minimal or no usable content/links: " + rec.getUrl());
                } else {
                    indexElasticSearchAsync(rec, webPageContent);
                    addUrlsToQueue(rec, innerUrls, rec.getDistance() + 1);
                    logger.info("Successfully crawled: " + rec.getUrl() + " with " + innerUrls.size() + " new URLs at " + new java.util.Date());
                }
            }

        } catch (Exception e) {
            String errorMsg = "Failed to crawl " + rec.getUrl() + ": " + e.getMessage() + " at " + new java.util.Date();
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
                        .timeout(60_000)
                        .followRedirects(true)
                        .maxBodySize(10 * 1024 * 1024)
                        .get();
            } catch (IOException e) {
                lastException = e;
                attempts++;
                logger.warn("Attempt " + attempts + "/" + maxRetries + " failed for " + url + ": " + e.getMessage() + " at " + new java.util.Date());

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
            URL parsedUrl = new URL(url);
            int statusCode = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; SimpleCrawler/1.0)")
                    .timeout(10_000)
                    .followRedirects(true)
                    .execute()
                    .statusCode();
            return statusCode < 400;
        } catch (MalformedURLException e) {
            logger.debug("Invalid URL format: " + url + ": " + e.getMessage() + " at " + new java.util.Date());
            return false;
        } catch (IOException e) {
            logger.debug("URL check failed for " + url + ": " + e.getMessage() + " at " + new java.util.Date());
            return false;
        }
    }

    private boolean isRobotsTxtBlocked(String url) {
        try {
            URL baseUrl = new URL(url);
            String robotsUrl = baseUrl.getProtocol() + "://" + baseUrl.getHost() + "/robots.txt";
            Document robots = Jsoup.connect(robotsUrl)
                    .userAgent("Mozilla/5.0 (compatible; SimpleCrawler/1.0)")
                    .timeout(10_000)
                    .ignoreHttpErrors(true)
                    .get();
            return robots.text().toLowerCase().contains("disallow: /");
        } catch (IOException e) {
            logger.debug("Failed to read robots.txt for " + url + ": " + e.getMessage() + " at " + new java.util.Date());
            return false;
        }
    }

    private String getAccessibilityErrorMessage(String url) {
        try {
            URL parsedUrl = new URL(url);
            int statusCode = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; SimpleCrawler/1.0)")
                    .timeout(10_000)
                    .followRedirects(true)
                    .execute()
                    .statusCode();
            if (statusCode >= 400) {
                return "URL is not accessible or returned an error (status code >= 400): " + url;
            }
            if (isRobotsTxtBlocked(url)) {
                return "Crawling may be restricted by robots.txt for " + url + " (proceeding with caution)";
            }
            return "No specific error (check logs for details)";
        } catch (MalformedURLException e) {
            return "Invalid URL format: " + url;
        } catch (IOException e) {
            return "Failed to check accessibility: " + e.getMessage();
        }
    }

    private boolean isUrlValid(String url) {
        try {
            new URL(url).toURI(); // Use toURI for better invalid URL detection
            return true;
        } catch (MalformedURLException | java.net.URISyntaxException e) {
            return false;
        }
    }

    public void updateCrawlStatusWithError(String crawlId, String errorMessage) {
        try {
            CrawlStatus current = readStatus(crawlId);
            if (current != null) {
                current.setErrorMessage(errorMessage);
                current.setStopReason(StopReason.userInitiated);
                setCrawlStatus(crawlId, current);
                logger.info("Crawl stopped with user-initiated error for " + crawlId + ": " + errorMessage + " at " + new java.util.Date());
            }
        } catch (Exception e) {
            logger.error("Failed to update crawl status with error: " + e.getMessage() + " at " + new java.util.Date(), e);
        }
    }

    public void stopCrawlGracefully(String crawlId, String stopReason) {
        try {
            isShuttingDown.set(true);
            CrawlStatus current = readStatus(crawlId);
            if (current != null) {
                current.setStopReason(StopReason.userInitiated);
                current.setErrorMessage(stopReason);
                setCrawlStatus(crawlId, current);
                // Clear the visited queue to stop processing pending messages
                redisTemplate.delete(crawlId + ".visited");
                logger.info("Gracefully stopping crawl " + crawlId + " with reason: " + stopReason + " at " + new java.util.Date());
            }
            // Allow existing tasks to complete
        } catch (Exception e) {
            logger.error("Failed to stop crawl gracefully: " + e.getMessage() + " at " + new java.util.Date(), e);
        }
    }

    private StopReason getStopReason(CrawlerRecord rec) {
        if (rec.getMaxDistance() >= 0 && rec.getDistance() > rec.getMaxDistance()) return StopReason.maxDistance;
        if (rec.getMaxUrls() > 0 && getVisitedUrls(rec.getCrawlId()) >= rec.getMaxUrls()) return StopReason.maxUrls;
        if (System.currentTimeMillis() >= rec.getMaxTime()) return StopReason.timeout;
        if (isShuttingDown.get()) return StopReason.userInitiated;
        return null;
    }

    private void addUrlsToQueue(CrawlerRecord rec, List<String> urls, int distance) throws InterruptedException, JsonProcessingException {
        logger.info("Adding URLs to queue: distance->" + distance + " amount->" + urls.size() + " at " + new java.util.Date());
        int currentVisited = getVisitedUrls(rec.getCrawlId());
        int remainingSlots = rec.getMaxUrls() > 0 ? rec.getMaxUrls() - currentVisited : Integer.MAX_VALUE;
        if (remainingSlots <= 0 || System.currentTimeMillis() >= rec.getMaxTime() || isShuttingDown.get()) return;
        List<String> urlsToAdd = urls.stream().limit(remainingSlots).collect(Collectors.toList());
        for (String url : urlsToAdd) {
            if (System.currentTimeMillis() >= rec.getMaxTime() || isShuttingDown.get()) break;
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
        logger.info("Extracted " + links.size() + " unique links at " + new java.util.Date());
        return links;
    }

    private void indexElasticSearchAsync(CrawlerRecord rec, Document webPageContent) {
        if (isShuttingDown.get()) {
            logger.info("Skipping indexing for " + rec.getUrl() + " due to shutdown at " + new java.util.Date());
            return;
        }
        logger.info("Scheduling Elasticsearch index for: " + rec.getUrl() + " at " + new java.util.Date());
        String text = webPageContent.body() != null ? webPageContent.body().text() : "";
        UrlSearchDoc searchDoc = UrlSearchDoc.of(rec.getCrawlId(), text, rec.getUrl(), rec.getBaseUrl(), rec.getDistance(), "html");
        try {
            indexExecutor.submit(() -> {
                try {
                    elasticSearch.addData(searchDoc);
                    logger.info("Indexed URL successfully: " + rec.getUrl() + " at " + new java.util.Date());
                } catch (Exception e) {
                    logger.error("Failed to index doc async: " + rec.getUrl() + " - " + e.getMessage() + " at " + new java.util.Date(), e);
                }
            });
        } catch (Exception e) {
            logger.warn("Failed to submit indexing task for " + rec.getUrl() + ": " + e.getMessage() + " at " + new java.util.Date());
        }
    }

    private void initCrawlInRedis(String crawlId) throws JsonProcessingException {
        // Reset shutdown flag for new crawl
        isShuttingDown.set(false);
        // Clear previous crawl data
        redisTemplate.delete(crawlId + ".status");
        redisTemplate.delete(crawlId + ".urls.count");
        redisTemplate.delete(crawlId + ".visited");
        long now = System.currentTimeMillis();
        setCrawlStatus(crawlId, CrawlStatus.of(0, now, 0, null));
        redisTemplate.opsForValue().set(crawlId + ".urls.count", "0");
        redisTemplate.opsForSet().add(crawlId + ".visited", crawlId);
        logger.info("Initialized crawl in Redis with ID: " + crawlId + " at " + new java.util.Date());
    }

    private void setCrawlStatus(String crawlId, CrawlStatus crawlStatus) throws JsonProcessingException {
        crawlStatus.setLastModifiedMillis(System.currentTimeMillis());
        redisTemplate.opsForValue().set(crawlId + ".status", om.writeValueAsString(crawlStatus));
        logger.debug("Set crawl status for ID: " + crawlId + " at " + new java.util.Date());
    }

    private CrawlStatus readStatus(String crawlId) {
        try {
            Object statusObj = redisTemplate.opsForValue().get(crawlId + ".status");
            if (statusObj == null) {
                logger.warn("No status found for crawlId: " + crawlId + " at " + new java.util.Date());
                return null;
            }
            return om.readValue(statusObj.toString(), CrawlStatus.class);
        } catch (Exception e) {
            logger.error("Failed reading crawl status for " + crawlId + ": " + e.getMessage() + " at " + new java.util.Date(), e);
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
                logger.warn("Failed to update crawl status after visited URL: " + e.getMessage() + " at " + new java.util.Date());
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
                logger.warn("No status found for crawlId: " + crawlId + " at " + new java.util.Date());
                long now = System.currentTimeMillis();
                return CrawlStatusOut.of(CrawlStatus.of(0, now, 0, null));
            }
            CrawlStatus cs = om.readValue(statusObj.toString(), CrawlStatus.class);
            cs.setNumPages(getVisitedUrls(crawlId));
            return CrawlStatusOut.of(cs);
        } catch (Exception e) {
            logger.error("Failed reading crawl status for " + crawlId + ": " + e.getMessage() + " at " + new java.util.Date(), e);
            long now = System.currentTimeMillis();
            return CrawlStatusOut.of(CrawlStatus.of(0, now, 0, null));
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            isShuttingDown.set(true);
            indexExecutor.shutdown();
            logger.info("Shutting down crawler executor service at " + new java.util.Date());
        } catch (Exception ignore) {}
    }
}