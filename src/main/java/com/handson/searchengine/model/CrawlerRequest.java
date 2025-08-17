package com.handson.searchengine.model;

public class CrawlerRequest {
    private String url;
    private int maxDistance;
    private int maxSeconds;
    private int maxUrls;

    public CrawlerRequest() {}

    public String getUrl() { return url; }
    public int getMaxDistance() { return maxDistance; }
    public int getMaxSeconds() { return maxSeconds; }
    public int getMaxUrls() { return maxUrls; }

    public void setUrl(String url) { this.url = url; }
    public void setMaxDistance(int maxDistance) { this.maxDistance = maxDistance; }
    public void setMaxSeconds(int maxSeconds) { this.maxSeconds = maxSeconds; }
    public void setMaxUrls(int maxUrls) { this.maxUrls = maxUrls; }
}
