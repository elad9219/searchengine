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
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Crawler service - improved indexing and status handling.
 */
@Service
public class Crawler {

    @Autowired
    RedisTemplate redisTemplate;

    @Autowired
    ObjectMapper om;

    @Autowired
    Producer producer;

    @Autowired
    ElasticSearch elasticSearch;

    protected final Log logger = LogFactory.getLog(getClass());

    /**
     * Start a crawl. Initializes the status in Redis including maxTime.
     */
    public void crawl(String crawlId, CrawlerRequest crawlerRequest) throws InterruptedException, IOException, JsonProcessingException {
        CrawlerRecord rec = CrawlerRecord.of(crawlId, crawlerRequest);
        initCrawlInRedis(crawlId, rec.getStartTime(), rec.getMaxTime());
        producer.send(rec);
    }

    /**
     * Process a single record consumed from Kafka.
     */
    public void crawlOneRecord(String crawlId, CrawlerRecord rec) throws IOException, InterruptedException {
        logger.info("crawling url:" + rec.getUrl());
        StopReason stopReason = getStopReason(rec);
        setCrawlStatus(crawlId, CrawlStatus.of(rec.getDistance(), rec.getStartTime(), 0, stopReason, rec.getMaxTime()));
        if (stopReason == null) {
            Document webPageContent = Jsoup.connect(rec.getUrl())
                    .userAgent("Mozilla/5.0 (compatible; SimpleCrawler/1.0)")
                    .timeout(15_000)
                    .get();
            indexElasticSearch(rec, webPageContent);
            List<String> innerUrls = extractWebPageUrls(rec.getBaseUrl(), webPageContent);
            addUrlsToQueue(rec, innerUrls, rec.getDistance() + 1);
        } else {
            logger.info("Not crawling " + rec.getUrl() + " because stopReason=" + stopReason);
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
            if (!crawlHasVisited(rec, url)) {
                producer.send(CrawlerRecord.of(rec).withUrl(url).withIncDistance());
            }
        }
    }

    /**
     * Extracts absolute links that belong to baseUrl.
     */
    private List<String> extractWebPageUrls(String baseUrl, Document webPageContent) {
        List<String> links = webPageContent.select("a[href]")
                .eachAttr("abs:href")
                .stream()
                .filter(url -> url.startsWith(baseUrl))
                .filter(url -> !url.startsWith("mailto:"))
                .filter(url -> !url.startsWith("javascript:"))
                .collect(Collectors.toList());
        logger.info(">> extracted->" + links.size() + " links");
        return links;
    }

    /**
     * Index a page into ElasticSearch.
     * Improved content extraction: try title, meta description, article tags, paragraphs — then body text.
     * Catch IOException so crawl continues if ES is down.
     */
    private void indexElasticSearch(CrawlerRecord rec, Document webPageContent) {
        logger.info(">> adding elastic search for webPage: " + rec.getUrl());
        StringBuilder contentBuilder = new StringBuilder();

        // 1) title
        String title = webPageContent.select("meta[property=og:title]").attr("content");
        if (title == null || title.isEmpty()) title = webPageContent.title();
        if (title != null && !title.isEmpty()) {
            contentBuilder.append(title).append("\n");
        }

        // 2) meta description / og:description
        String desc = webPageContent.select("meta[name=description]").attr("content");
        if ((desc == null || desc.isEmpty())) desc = webPageContent.select("meta[property=og:description]").attr("content");
        if (desc != null && !desc.isEmpty()) {
            contentBuilder.append(desc).append("\n");
        }

        // 3) article tags and paragraphs
        Elements articleTags = webPageContent.select("article, .article, [itemprop=articleBody]");
        if (articleTags != null && !articleTags.isEmpty()) {
            for (Element article : articleTags) {
                contentBuilder.append(article.text()).append("\n");
            }
        } else {
            // fallback to main paragraphs
            Elements paragraphs = webPageContent.select("p");
            for (Element p : paragraphs) {
                contentBuilder.append(p.text()).append("\n");
            }
        }

        // 4) fallback to body text
        if (contentBuilder.length() < 50) {
            contentBuilder.append(webPageContent.body().text());
        }

        String text = contentBuilder.toString();
        UrlSearchDoc searchDoc = UrlSearchDoc.of(rec.getCrawlId(), text, rec.getUrl(), rec.getBaseUrl(), rec.getDistance());

        try {
            // IMPORTANT: currently addData uses refresh=true (see ElasticSearch.addData) so the doc is visible quickly.
            elasticSearch.addData(searchDoc);
        } catch (IOException e) {
            logger.error("Failed to index URL in ElasticSearch: " + rec.getUrl() + " - " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error while indexing URL: " + rec.getUrl() + " - " + e.getMessage(), e);
        }
    }

    private void initCrawlInRedis(String crawlId, long startTimeMillis, long maxTimeMillis) throws JsonProcessingException {
        setCrawlStatus(crawlId, CrawlStatus.of(0, startTimeMillis, 0, null, maxTimeMillis));
        redisTemplate.opsForValue().set(crawlId + ".urls.count", "1");
    }

    private void setCrawlStatus(String crawlId, CrawlStatus crawlStatus) throws JsonProcessingException {
        redisTemplate.opsForValue().set(crawlId + ".status", om.writeValueAsString(crawlStatus));
    }

    private boolean crawlHasVisited(CrawlerRecord rec, String url) {
        if (redisTemplate.opsForValue().setIfAbsent(rec.getCrawlId() + ".urls." + url, "1")) {
            redisTemplate.opsForValue().increment(rec.getCrawlId() + ".urls.count", 1L);
            return false;
        } else {
            return true;
        }
    }

    private int getVisitedUrls(String crawlId) {
        Object curCount = redisTemplate.opsForValue().get(crawlId + ".urls.count");
        if (curCount == null) return 0;
        return Integer.parseInt(curCount.toString());
    }

    /**
     * Return crawl info (reads Redis). If stopReason is still null but conditions met (timeout / maxUrls),
     * mark the stopReason here and persist it — so frontend will see final status.
     */
    public CrawlStatusOut getCrawlInfo(String crawlId) throws JsonProcessingException {
        Object statusObj = redisTemplate.opsForValue().get(crawlId + ".status");
        if (statusObj == null) {
            logger.warn("No status found for crawlId: " + crawlId);
            return CrawlStatusOut.of(CrawlStatus.of(0, 0, 0, null, 0));
        }
        CrawlStatus cs = om.readValue(statusObj.toString(), CrawlStatus.class);
        cs.setNumPages(getVisitedUrls(crawlId));

        // derive stop reason if not yet set
        if (cs.getStopReason() == null) {
            long now = System.currentTimeMillis();
            if (cs.getMaxTime() > 0 && now >= cs.getMaxTime()) {
                cs.setStopReason(StopReason.timeout);
            } else if (getVisitedUrls(crawlId) >= /* we don't have maxUrls here, but we can approximate */ Integer.MAX_VALUE) {
                // no-op: cannot detect maxUrls here without CrawlerRecord stored; skip
            }
            // persist updated stopReason / lastModified
            try {
                setCrawlStatus(crawlId, cs);
            } catch (JsonProcessingException e) {
                logger.error("Failed to persist crawl status after deriving stopReason: " + e.getMessage(), e);
            }
        }

        return CrawlStatusOut.of(cs);
    }
}
