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
    void omittedLimitsAndLegacySentinelsUseBoundedDefaults() throws Exception {
        CrawlerRequest omitted = new CrawlerRequest();
        omitted.setUrl("https://example.com");
        controller.crawl(omitted);
        assertEquals(60, omitted.getMaxSeconds());
        assertEquals(2, omitted.getMaxDistance());
        assertEquals(25, omitted.getMaxUrls());

        CrawlerRequest legacy = request("https://example.com");
        legacy.setMaxUrls(-1);
        legacy.setMaxDistance(-1);
        controller.crawl(legacy);
        assertEquals(2, legacy.getMaxDistance());
        assertEquals(25, legacy.getMaxUrls());
    }

    @Test
    void rejectsExcessiveBudgetsBeforeStartingWork() {
        CrawlerRequest seconds = request("https://example.com");
        seconds.setMaxSeconds(121);
        CrawlerRequest depth = request("https://example.com");
        depth.setMaxDistance(5);
        CrawlerRequest pages = request("https://example.com");
        pages.setMaxUrls(101);
        for (CrawlerRequest request : java.util.List.of(seconds, depth, pages)) {
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> controller.crawl(request));
            assertEquals(400, error.getStatusCode().value());
        }
        verifyNoInteractions(crawler);
    }

    @Test
    void submissionFailureIsNotReturnedAsASuccessfulCrawlId() throws Exception {
        doThrow(new IOException("Broker unavailable")).when(crawler).crawl(anyString(), any());
        assertThrows(IOException.class, () -> controller.crawl(request("https://example.com")));
    }
}