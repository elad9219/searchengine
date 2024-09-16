package com.handson.searchengine.model;

public class CrawlerRecord {
    String crawlId;
    String baseUrl;
    String url;
    int distance;
    int maxDistance;
    long startTime;
    long maxTime;
    int maxUrls;

    public static CrawlerRecord of(String crawlId, CrawlerRequest r) {
        long startTime = System.currentTimeMillis();
        CrawlerRecord res = new CrawlerRecord();
        res.crawlId = crawlId;
        res.baseUrl = r.url;
        res.url = r.getUrl();
        res.distance = 0;
        res.startTime = startTime;
        res.maxTime = startTime + 1000L * r.maxSeconds;
        res.maxDistance = r.maxDistance;
        res.maxUrls = r.maxUrls;
        return res;
    }

    public static CrawlerRecord of(CrawlerRecord r) {
        CrawlerRecord res = new CrawlerRecord();
        res.crawlId = r.crawlId;
        res.baseUrl = r.baseUrl;
        res.url = r.url;
        res.distance = r.distance;
        res.maxTime = r.maxTime;
        res.startTime = r.startTime;
        res.maxDistance = r.maxDistance;
        res.maxUrls = r.maxUrls;
        return res;
    }
    public CrawlerRecord withUrl(String url) {
        this.url = url;
        return this;
    }


    public CrawlerRecord withIncDistance() {
        distance += 1;
        return this;
    }

    public String getCrawlId() {
        return crawlId;
    }

    public long getStartTime() {
        return startTime;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getUrl() {
        return url;
    }

    public int getDistance() {
        return distance;
    }

    public int getMaxDistance() {
        return maxDistance;
    }

    public long getMaxTime() {
        return maxTime;
    }

    public int getMaxUrls() {
        return maxUrls;
    }
}
