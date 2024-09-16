package com.handson.searchengine.crawler;

import com.handson.searchengine.model.CrawlStatus;
import com.handson.searchengine.model.CrawlerRecord;
import com.handson.searchengine.model.CrawlerRequest;
import com.handson.searchengine.model.StopReason;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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

    protected final Log logger = LogFactory.getLog(getClass());

    public static final int MAX_CAPACITY = 100000;
    private Set<String> visitedUrls = new HashSet<>();
    private BlockingQueue<CrawlerRecord> queue = new ArrayBlockingQueue<CrawlerRecord>(MAX_CAPACITY);
    private int curDistance = 0;
    private long startTime = 0;
    private StopReason stopReason;
    public CrawlStatus crawl(String crawlId, CrawlerRequest crawlerRequest) throws InterruptedException, IOException {
        visitedUrls.clear();
        queue.clear();
        curDistance = 0;
        startTime = System.currentTimeMillis();
        stopReason = null;
        queue.put(CrawlerRecord.of(crawlId, crawlerRequest));
        while (!queue.isEmpty() && getStopReason(queue.peek()) == null) {
            CrawlerRecord rec = queue.poll();
            logger.info("crawling url:" + rec.getUrl());
            Document webPageContent = Jsoup.connect(rec.getUrl()).get();
            List<String> innerUrls = extractWebPageUrls(rec.getBaseUrl(), webPageContent);
            addUrlsToQueue(rec, innerUrls, rec.getDistance() +1);
        }
        stopReason = queue.isEmpty() ? null : getStopReason(queue.peek());
        return CrawlStatus.of(curDistance, startTime, visitedUrls.size(), stopReason);

    }

    private StopReason getStopReason(CrawlerRecord rec) {
        if (rec.getDistance() == rec.getMaxDistance() +1) return StopReason.maxDistance;
        if (visitedUrls.size() >= rec.getMaxUrls()) return StopReason.maxUrls;
        if (System.currentTimeMillis() >= rec.getMaxTime()) return StopReason.timeout;
        return null;
    }


    private void addUrlsToQueue(CrawlerRecord rec, List<String> urls, int distance) throws InterruptedException {
        logger.info(">> adding urls to queue: distance->" + distance + " amount->" + urls.size());
        curDistance = distance;
        for (String url : urls) {
            if (!visitedUrls.contains(url)) {
                visitedUrls.add(url);
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


}
