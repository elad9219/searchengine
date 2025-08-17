package com.handson.searchengine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CrawlStatusOut {
    private int distance;
    private long startTimeMillis;
    private long lastModifiedMillis;
    private String stopReason;
    private int numPages;

    public static CrawlStatusOut of(CrawlStatus in) {
        CrawlStatusOut res = new CrawlStatusOut();
        res.distance = in.getDistance();
        res.startTimeMillis = in.getStartTimeMillis();
        res.lastModifiedMillis = in.getLastModifiedMillis();
        res.stopReason = (in.getStopReason() == null) ? null : in.getStopReason().toString();
        res.numPages = in.getNumPages();
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

    public String getStopReason() {
        return stopReason;
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