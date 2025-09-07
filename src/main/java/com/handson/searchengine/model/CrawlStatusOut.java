package com.handson.searchengine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CrawlStatusOut {
    private final int distance;
    private final long startTimeMillis;
    private final long lastModifiedMillis;
    private final String stopReason;
    private final String errorMessage;
    private final int numPages;

    private CrawlStatusOut(int distance, long startTimeMillis, long lastModifiedMillis, int numPages, String stopReason, String errorMessage) {
        this.distance = distance;
        this.startTimeMillis = startTimeMillis;
        this.lastModifiedMillis = lastModifiedMillis;
        this.numPages = numPages;
        this.stopReason = stopReason;
        this.errorMessage = errorMessage;
    }

    public static CrawlStatusOut of(CrawlStatus in) {
        if (in == null) {
            long now = System.currentTimeMillis();
            return new CrawlStatusOut(0, now, now, 0, null, null);
        }
        return new CrawlStatusOut(
                in.getDistance(),
                in.getStartTimeMillis(),
                in.getLastModifiedMillis(),
                in.getNumPages(),
                in.getStopReason() != null ? in.getStopReason().name() : null,
                in.getErrorMessage()
        );
    }

    public String getErrorMessage() {
        return errorMessage;
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

    public String getStopReason() {
        return stopReason;
    }

    public StopReason getStopReasonAsEnum() {
        return stopReason != null ? StopReason.valueOf(stopReason) : null;
    }

    public int getNumPages() {
        return numPages;
    }

    @Override
    public String toString() {
        return "CrawlStatusOut{" +
                "distance=" + distance +
                ", startTimeMillis=" + startTimeMillis +
                ", lastModifiedMillis=" + lastModifiedMillis +
                ", stopReason='" + stopReason + '\'' +
                ", numPages=" + numPages +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CrawlStatusOut that = (CrawlStatusOut) o;
        return distance == that.distance &&
                startTimeMillis == that.startTimeMillis &&
                lastModifiedMillis == that.lastModifiedMillis &&
                numPages == that.numPages &&
                Objects.equals(stopReason, that.stopReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(distance, startTimeMillis, lastModifiedMillis, stopReason, numPages);
    }
}