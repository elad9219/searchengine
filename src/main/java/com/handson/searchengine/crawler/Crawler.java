package com.handson.searchengine.crawler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.handson.searchengine.model.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class Crawler {

    @Autowired
    RedisTemplate redisTemplate;

    @Autowired
    ObjectMapper om;

    protected final Log logger = LogFactory.getLog(getClass());

    public static final int MAX_CAPACITY = 100000;
    private BlockingQueue<CrawlerRecord> queue = new ArrayBlockingQueue<CrawlerRecord>(MAX_CAPACITY);
    public void crawl(String crawlId, CrawlerRequest crawlerRequest) throws InterruptedException, IOException {
        initCrawlInRedis(crawlId);
        queue.clear();
        queue.put(CrawlerRecord.of(crawlId, crawlerRequest));
        while (!queue.isEmpty() && getStopReason(queue.peek()) == null) {
            CrawlerRecord rec = queue.poll();
            logger.info("crawling url:" + rec.getUrl());
            setCrawlStatus(crawlId, CrawlStatus.of(rec.getDistance(), rec.getStartTime(), 0, null));
            Document webPageContent = Jsoup.connect(rec.getUrl()).get();
            List<String> innerUrls = extractWebPageUrls(rec.getBaseUrl(), webPageContent);
            addUrlsToQueue(rec, innerUrls, rec.getDistance() +1);
        }

        CrawlerRecord rec = queue.peek();
        var stopReason =  getStopReason(rec);
        setCrawlStatus(crawlId, CrawlStatus.of(rec.getDistance(), rec.getStartTime(), 0, stopReason));

    }

    private StopReason getStopReason(CrawlerRecord rec) {
        if (rec.getDistance() == rec.getMaxDistance() +1) return StopReason.maxDistance;
        if (getVisitedUrls(rec.getCrawlId()) >= rec.getMaxUrls()) return StopReason.maxUrls;
        if (System.currentTimeMillis() >= rec.getMaxTime()) return StopReason.timeout;
        return null;
    }


    private void addUrlsToQueue(CrawlerRecord rec, List<String> urls, int distance) throws InterruptedException {
        logger.info(">> adding urls to queue: distance->" + distance + " amount->" + urls.size());
        for (String url : urls) {
            if (!crawlHasVisited(rec, url)) {
                queue.put(CrawlerRecord.of(rec).withUrl(url).withIncDistance()) ;
            }
        }
    }

    private List<String> extractWebPageUrls(String baseUrl, Document webPageContent) {
        List<String> links = webPageContent.select("a[href]")
                .eachAttr("abs:href")
                .stream()
                .filter(url -> url.startsWith(baseUrl))
                .collect(Collectors.toList());
        logger.info(">> extracted->" + links.size() + " links");

        return links;
    }

    private void initCrawlInRedis(String crawlId) throws JsonProcessingException {
        setCrawlStatus(crawlId, CrawlStatus.of(0, System.currentTimeMillis(),0,  null));
        redisTemplate.opsForValue().set(crawlId + ".urls.count","1");
    }
    private void setCrawlStatus(String crawlId, CrawlStatus crawlStatus) throws JsonProcessingException {
        redisTemplate.opsForValue().set(crawlId + ".status", om.writeValueAsString(crawlStatus));
    }

    private boolean crawlHasVisited(CrawlerRecord rec, String url) {
        if ( redisTemplate.opsForValue().setIfAbsent(rec.getCrawlId() + ".urls." + url, "1")) {
            redisTemplate.opsForValue().increment(rec.getCrawlId() + ".urls.count",1L);
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

    public CrawlStatusOut getCrawlInfo(String crawlId) throws JsonProcessingException {
        CrawlStatus cs = om.readValue(redisTemplate.opsForValue().get(crawlId + ".status").toString(),CrawlStatus.class);
        cs.setNumPages(getVisitedUrls(crawlId));
        return CrawlStatusOut.of(cs);
    }
}
