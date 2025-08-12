package com.handson.searchengine.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.handson.searchengine.util.Dates;
import org.joda.time.LocalDateTime;

import java.util.Date;

public class CrawlStatusOut {
    int distance;
    long startTime; // epoch millis
    StopReason stopReason;
    long lastModified; // epoch millis
    long numPages = 0;
    long maxTime; // epoch millis

    public static CrawlStatusOut of(CrawlStatus in) {
        CrawlStatusOut res = new CrawlStatusOut();
        res.distance = in.getDistance();
        res.startTime = in.getStartTime();
        res.lastModified = in.getLastModified();
        res.stopReason = in.getStopReason();
        res.numPages = in.getNumPages();
        res.maxTime = in.getMaxTime();
        return res;
    }

    // Keep existing JSON formatted human-readable helpers
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("startTime")
    public LocalDateTime calcStartTime() {
        return Dates.atLocalTime(new Date(startTime));
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("lastModified")
    public LocalDateTime calcLastModified() {
        return Dates.atLocalTime(new Date(lastModified));
    }

    @JsonProperty("startTimeMillis")
    public long getStartTimeMillis() { return startTime; }

    @JsonProperty("lastModifiedMillis")
    public long getLastModifiedMillis() { return lastModified; }

    @JsonProperty("maxTimeMillis")
    public long getMaxTimeMillis() { return maxTime; }

    public int getDistance() { return distance; }
    public StopReason getStopReason() { return stopReason; }
    public long getNumPages() { return numPages; }

    public void setDistance(int distance) { this.distance = distance; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    public void setStopReason(StopReason stopReason) { this.stopReason = stopReason; }
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }
    public void setNumPages(long numPages) { this.numPages = numPages; }
    public void setMaxTime(long maxTime) { this.maxTime = maxTime; }
}
