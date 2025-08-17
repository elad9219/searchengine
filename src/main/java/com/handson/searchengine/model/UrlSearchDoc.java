package com.handson.searchengine.model;

import java.util.Objects;

public class UrlSearchDoc {
    private String url;
    private String baseUrl;
    private String content;
    private int level;
    private String crawlId;
    private String contentType; // הפרמטר הנוסף (לפי החתימה אצלך)

    // חתימה עם 6 פרמטרים – כמו שהקומפיילר שלך דרש
    public static UrlSearchDoc of(String crawlId, String content, String url, String baseUrl, int level, String contentType) {
        UrlSearchDoc res = new UrlSearchDoc();
        res.crawlId = crawlId;
        res.url = url;
        res.baseUrl = baseUrl;
        res.content = content;
        res.level = level;
        res.contentType = contentType;
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
                ", contentType='" + contentType + '\'' +
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
                Objects.equals(content, that.content) &&
                Objects.equals(contentType, that.contentType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, baseUrl, content, level, contentType);
    }

    public String getCrawlId() {
        return crawlId;
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

    public String getContentType() {
        return contentType;
    }
}
