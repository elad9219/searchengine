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
import java.util.List;
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
        initCrawlInRedis(crawlId);
        CrawlerRecord first = CrawlerRecord.of(crawlerRequest).withCrawlId(crawlId);
        producer.send(first);
    }

    public void crawlOneRecord(String crawlId, CrawlerRecord rec) throws IOException, InterruptedException {
        logger.info("crawling url:" + rec.getUrl());
        StopReason stopReason = getStopReason(rec);
        CrawlStatus current = readStatus(crawlId);
        long startTime = current != null ? current.getStartTimeMillis() : System.currentTimeMillis();
        try {
            setCrawlStatus(crawlId, CrawlStatus.of(rec.getDistance(), startTime, getVisitedUrls(crawlId), stopReason));
        } catch (JsonProcessingException e) {
            logger.warn("Failed to set crawl status at start: " + e.getMessage(), e);
        }
        if (stopReason == null) {
            Document webPageContent = null;
            try {
                webPageContent = Jsoup.connect(rec.getUrl())
                        .userAgent("Mozilla/5.0 (compatible; SimpleCrawler/1.0)")
                        .timeout(15_000)
                        .get();
            } catch (Exception e) {
                logger.warn("Failed to fetch URL " + rec.getUrl() + " : " + e.getMessage());
            }

            if (webPageContent != null) {
                indexElasticSearchAsync(rec, webPageContent);
                List<String> innerUrls = extractWebPageUrls(rec.getBaseUrl(), webPageContent);
                addUrlsToQueue(rec, innerUrls, rec.getDistance() + 1);
            }
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("Not crawling " + rec.getUrl() + " because stopReason=" + stopReason);
            }
        }
    }

    private StopReason getStopReason(CrawlerRecord rec) {
        if (rec.getDistance() == rec.getMaxDistance() + 1) return StopReason.maxDistance;
        if (getVisitedUrls(rec.getCrawlId()) >= rec.getMaxUrls()) return StopReason.maxUrls;
        if (System.currentTimeMillis() >= rec.getMaxTime()) return StopReason.timeout;
        return null;
    }

    private void addUrlsToQueue(CrawlerRecord rec, List<String> urls, int distance) throws InterruptedException, JsonProcessingException {
        logger.info(">> adding urls to queue: distance->" + distance + " amount->" + urls.size());
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
                .collect(Collectors.toList());
        logger.info(">> extracted->" + links.size() + " links");
        return links;
    }

    private void indexElasticSearchAsync(CrawlerRecord rec, Document webPageContent) {
        logger.info(">> scheduling elasticsearch index for webPage: " + rec.getUrl());
        String text = webPageContent.body() != null ? webPageContent.body().text() : "";
        UrlSearchDoc searchDoc = UrlSearchDoc.of(rec.getCrawlId(), text, rec.getUrl(), rec.getBaseUrl(), rec.getDistance(), "html");
        indexExecutor.submit(() -> {
            try {
                elasticSearch.addData(searchDoc);
            } catch (Exception e) {
                logger.error("Failed to index doc async: " + rec.getUrl() + " - " + e.getMessage(), e);
            }
        });
    }

    private void initCrawlInRedis(String crawlId) throws JsonProcessingException {
        long now = System.currentTimeMillis();
        setCrawlStatus(crawlId, CrawlStatus.of(0, now, 0, null));
        redisTemplate.opsForValue().set(crawlId + ".urls.count", "0");
    }

    private void setCrawlStatus(String crawlId, CrawlStatus crawlStatus) throws JsonProcessingException {
        redisTemplate.opsForValue().set(crawlId + ".status", om.writeValueAsString(crawlStatus));
    }

    private CrawlStatus readStatus(String crawlId) {
        try {
            Object statusObj = redisTemplate.opsForValue().get(crawlId + ".status");
            if (statusObj == null) return null;
            return om.readValue(statusObj.toString(), CrawlStatus.class);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean crawlHasVisited(CrawlerRecord rec, String url) {
        boolean wasAbsent = redisTemplate.opsForValue().setIfAbsent(rec.getCrawlId() + ".urls." + url, "1");
        if (wasAbsent) {
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
        } catch (Exception ignore) {}
    }
}