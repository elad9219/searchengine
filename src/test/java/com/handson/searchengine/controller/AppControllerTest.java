package com.handson.searchengine.controller;

import com.handson.searchengine.crawler.Crawler;
import com.handson.searchengine.model.CrawlerRequest;
import com.handson.searchengine.util.ElasticSearch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AppControllerTest {
    private Crawler crawler;
    private AppController controller;

    @BeforeEach
    void setup() {
        crawler = mock(Crawler.class);
        controller = new AppController(crawler, mock(ElasticSearch.class));
    }

    private CrawlerRequest request(String url) {
        CrawlerRequest request = new CrawlerRequest();
        request.setUrl(url);
        request.setMaxSeconds(20);
        request.setMaxDistance(1);
        request.setMaxUrls(1);
        return request;
    }

    @Test
    void preservesHostAndEncodedPathWhileRemovingFragment() throws Exception {
        CrawlerRequest request = request("https://example.com/a%2Fb?q=a%26b#section");
        String id = controller.crawl(request);
        assertEquals("https://example.com/a%2Fb?q=a%26b", request.getUrl());
        assertTrue(id.matches("[a-f0-9]{16}"));
        verify(crawler).crawl(id, request);
    }

    @Test
    void addsHttpsWithoutInventingAHostPrefix() throws Exception {
        CrawlerRequest request = request("example.com/page");
        controller.crawl(request);
        assertEquals("https://example.com/page", request.getUrl());
    }

    @Test
    void rejectsInvalidInputBeforeStartingWork() {
        assertThrows(ResponseStatusException.class, () -> controller.crawl(request("  ")));
        assertThrows(ResponseStatusException.class, () -> controller.crawl(request("ftp://example.com")));
        CrawlerRequest request = request("https://example.com");
        request.setMaxUrls(0);
        assertThrows(ResponseStatusException.class, () -> controller.crawl(request));
        verifyNoInteractions(crawler);
    }

    @Test
    void submissionFailureIsNotReturnedAsASuccessfulCrawlId() throws Exception {
        doThrow(new IOException("Broker unavailable")).when(crawler).crawl(anyString(), any());
        assertThrows(IOException.class, () -> controller.crawl(request("https://example.com")));
    }
}