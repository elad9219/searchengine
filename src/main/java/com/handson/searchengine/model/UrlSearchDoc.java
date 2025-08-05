package com.handson.searchengine.model;

import java.util.Objects;

public class UrlSearchDoc {
    private String url;
    private String baseUrl;
    private String content;
    private int level;
    private String crawlId;

    public static UrlSearchDoc of(String crawlId, String content, String url, String baseUrl, int level) {
        UrlSearchDoc res = new UrlSearchDoc();
        res.crawlId = crawlId;
        res.url = url;
        res.baseUrl = baseUrl;
        res.content = content;
        res.level = level;
        return res;
    }

    @Override
    public String toString() {
        return "UrlSearchDoc{" +
                "crawlId='" + crawlId + '\'' +
                ", url='" + url + '\'' +
                ", baseUrl='" + baseUrl + '\'' +
                ", content='" + content + '\'' +
                ", level=" + level +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UrlSearchDoc that = (UrlSearchDoc) o;
        return level == that.level &&
                Objects.equals(url, that.url) &&
                Objects.equals(baseUrl, that.baseUrl) &&
                Objects.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, baseUrl, content, level);
    }

    public String getCrawlId() {
        return crawlId;
    }

    public void setCrawlId(String crawlId) {
        this.crawlId = crawlId;
    }

    public String getUrl() {
        return url;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getContent() {
        return content;
    }

    public int getLevel() {
        return level;
    }
}
