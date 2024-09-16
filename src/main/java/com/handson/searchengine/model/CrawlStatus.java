package com.handson.searchengine.model;

public class CrawlStatus {
    int distance;
    long startTime;
    StopReason stopReason;
    long lastModified;
    long numPages = 0;

    public static CrawlStatus of(int distance, long startTime, int numPages, StopReason stopReason) {
        CrawlStatus res = new CrawlStatus();
        res.distance = distance;
        res.startTime =  startTime;
        res.lastModified = System.currentTimeMillis();
        res.stopReason = stopReason;
        res.numPages = numPages;
        return res;
    }

    public int getDistance() {
        return distance;
    }

    public long getLastModified() {
        return lastModified;
    }

    public long getStartTime() {
        return startTime;
    }

    public StopReason getStopReason() {
        return stopReason;
    }

    public long getNumPages() {
        return numPages;
    }

    public void setNumPages(long numPages) {
        this.numPages = numPages;
    }
}
