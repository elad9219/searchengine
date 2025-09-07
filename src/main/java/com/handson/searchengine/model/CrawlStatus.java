package com.handson.searchengine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public class CrawlStatus {
    private int distance;
    private long startTimeMillis;
    private long lastModifiedMillis;
    private StopReason stopReason;
    private String errorMessage;
    private int numPages;

    public CrawlStatus(int distance, long startTime, int numPages, StopReason stopReason) {
        this.distance = distance;
        this.startTimeMillis = startTime;
        this.numPages = numPages;
        this.lastModifiedMillis = startTime;
        this.stopReason = stopReason;
    }

    public static CrawlStatus of(int distance, long startTime, int numPages, StopReason stopReason) {
        return new CrawlStatus(distance, startTime, numPages, stopReason);
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

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}