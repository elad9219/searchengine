package com.handson.searchengine.model;

public class CrawlStatus {
    int distance;
    long startTime;
    StopReason stopReason;
    long lastModified;
    long numPages = 0;
    long maxTime; // epoch millis when crawl must stop

    public static CrawlStatus of(int distance, long startTime, int numPages, StopReason stopReason, long maxTime) {
        CrawlStatus res = new CrawlStatus();
        res.distance = distance;
        res.startTime = startTime;
        res.lastModified = System.currentTimeMillis();
        res.stopReason = stopReason;
        res.numPages = numPages;
        res.maxTime = maxTime;
        return res;
    }

    public int getDistance() { return distance; }
    public long getLastModified() { return lastModified; }
    public long getStartTime() { return startTime; }
    public StopReason getStopReason() { return stopReason; }
    public long getNumPages() { return numPages; }
    public long getMaxTime() { return maxTime; }

    public void setNumPages(long numPages) { this.numPages = numPages; }
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }
    public void setStopReason(StopReason stopReason) { this.stopReason = stopReason; }
}
