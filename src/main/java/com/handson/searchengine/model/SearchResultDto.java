package com.handson.searchengine.model;

public class SearchResultDto {
    private String url;
    private String snippet;

    public SearchResultDto() {}

    public SearchResultDto(String url, String snippet) {
        this.url = url;
        this.snippet = snippet;
    }

    public String getUrl() {
        return url;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }
}
