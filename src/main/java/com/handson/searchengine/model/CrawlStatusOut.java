package com.handson.searchengine.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.handson.searchengine.util.Dates;
import org.joda.time.LocalDateTime;

import java.util.Date;

public class CrawlStatusOut {
    int distance;
    long startTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("startTime")
    public LocalDateTime calcStartTime() {
        return Dates.atLocalTime(new Date(startTime));
    }

    StopReason stopReason;
    long lastModified;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("lastModified")
    public LocalDateTime calcLastModified() {
        return Dates.atLocalTime(new Date(lastModified));
    }

    long numPages = 0;

    public static CrawlStatusOut of(CrawlStatus in) {
        CrawlStatusOut res = new CrawlStatusOut();
        res.distance = in.distance;
        res.startTime =  in.startTime;
        res.lastModified = in.lastModified;
        res.stopReason = in.stopReason;
        res.numPages = in.numPages;
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

