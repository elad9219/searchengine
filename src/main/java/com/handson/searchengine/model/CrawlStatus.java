package com.handson.searchengine.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CrawlStatus {
    private int distance;
    private long startTimeMillis;
    private int numPages;
    private StopReason stopReason;
    private String errorMessage;
    private long lastModifiedMillis;

    public CrawlStatus(int distance, long startTimeMillis, int numPages, StopReason stopReason) {
        this.distance = distance;
        this.startTimeMillis = startTimeMillis;
        this.numPages = numPages;
        this.stopReason = stopReason;
    }

    public int getDistance() { return distance; }
    public long getStartTimeMillis() { return startTimeMillis; }
    public int getNumPages() { return numPages; }
    public StopReason getStopReason() { return stopReason; }
    public String getErrorMessage() { return errorMessage; }
    public long getLastModifiedMillis() { return lastModifiedMillis; }

    public void setNumPages(int numPages) { this.numPages = numPages; }
    public void setStopReason(StopReason stopReason) { this.stopReason = stopReason; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setLastModifiedMillis(long lastModifiedMillis) { this.lastModifiedMillis = lastModifiedMillis; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CrawlStatus that = (CrawlStatus) o;
        return distance == that.distance &&
                startTimeMillis == that.startTimeMillis &&
                numPages == that.numPages &&
                stopReason == that.stopReason &&
                Objects.equals(errorMessage, that.errorMessage) &&
                lastModifiedMillis == that.lastModifiedMillis;
    }

    @Override
    public int hashCode() {
        return Objects.hash(distance, startTimeMillis, numPages, stopReason, errorMessage, lastModifiedMillis);
    }

    public static CrawlStatus of(int distance, long startTimeMillis, int numPages, StopReason stopReason) {
        return new CrawlStatus(distance, startTimeMillis, numPages, stopReason);
    }
}