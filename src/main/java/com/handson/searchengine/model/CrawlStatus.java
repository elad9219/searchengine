package com.handson.searchengine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public class CrawlStatus {
    private int distance;
    private long startTimeMillis;
    private long lastModifiedMillis;
    private StopReason stopReason;
    private int numPages;

    public static CrawlStatus of(int distance, long startTime, int numPages, StopReason stopReason) {
        CrawlStatus res = new CrawlStatus();
        res.distance = distance;
        res.startTimeMillis = startTime;
        res.numPages = numPages;
        res.lastModifiedMillis = startTime;
        res.stopReason = stopReason;
        return res;
    }

    public int getDistance() {
        return distance;
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public long getLastModifiedMillis() {
        return lastModifiedMillis;
    }

    public StopReason getStopReason() {
        return stopReason;
    }

    public int getNumPages() {
        return numPages;
    }

    public void setDistance(int distance) {
        this.distance = distance;
    }

    public void setStartTimeMillis(long startTimeMillis) {
        this.startTimeMillis = startTimeMillis;
    }

    public void setLastModifiedMillis(long lastModifiedMillis) {
        this.lastModifiedMillis = lastModifiedMillis;
    }

    public void setStopReason(StopReason stopReason) {
        this.stopReason = stopReason;
    }

    public void setNumPages(int numPages) {
        this.numPages = numPages;
    }
}