package com.handson.searchengine.crawler;

import com.handson.searchengine.kafka.Producer;
import com.handson.searchengine.model.*;
import com.handson.searchengine.util.ElasticSearch;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CrawlerTest {
    private CrawlStateStore states;
    private StringRedisTemplate redis;
    private Producer producer;
    private ElasticSearch search;
    private Crawler crawler;
    private CrawlerRecord record;

    @BeforeEach
    void setup() {
        states = mock(CrawlStateStore.class);
        redis = mock(StringRedisTemplate.class, RETURNS_DEEP_STUBS);
        producer = mock(Producer.class);
        search = mock(ElasticSearch.class);
        crawler = spy(new Crawler(states, redis, producer, search));
        CrawlerRequest request = new CrawlerRequest();
        request.setUrl("https://example.com/page");
        request.setMaxSeconds(20);
        request.setMaxDistance(1);
        request.setMaxUrls(1);
        record = CrawlerRecord.of(request).withCrawlId("test");
        when(states.claim(record)).thenReturn(true);
        when(states.status("test")).thenReturn(CrawlStatus.of(0, 1, 0, null));
    }

    @Test
    void duplicateDeliveryDoesNotFetchOrIndexAgain() throws Exception {
        when(states.claim(record)).thenReturn(false);
        crawler.crawlOneRecord("test", record);
        verify(crawler, never()).fetch(any());
        verifyNoInteractions(redis, producer, search);
    }

    @Test
    void countsPageOnlyAfterProcessingAndChecksChildrenSeparately() throws Exception {
        doReturn(Jsoup.parse("<p>Readable content</p><a href='/child'>Next</a>",
                "https://example.com/page")).when(crawler).fetch(record);
        crawler.crawlOneRecord("test", record);
        verify(search).addData(any());
        verify(states).queue(argThat(child -> child.getDistance() == 1
                && child.getUrl().equals("https://example.com/child")));
        verify(states).finish(record);
        verify(states, never()).fail(anyString(), anyString());
    }

    @Test
    void stoppedCrawlDoesNotSaveFetchedContent() throws Exception {
        when(states.status("test")).thenReturn(CrawlStatus.of(0, 1, 0, StopReason.userInitiated));
        doReturn(Jsoup.parse("<p>Readable content</p>")).when(crawler).fetch(record);
        crawler.crawlOneRecord("test", record);
        verifyNoInteractions(redis, producer, search);
        verify(states, never()).finish(any());
    }

    @Test
    void fetchFailureIsReportedAsAnError() throws Exception {
        doThrow(new IOException("Fixture failure")).when(crawler).fetch(record);
        crawler.crawlOneRecord("test", record);
        verify(states).fail("test", "Could not finish processing a page");
        verify(states, never()).finish(any());
    }

    @Test
    void stoppingOneCrawlTargetsOnlyItsOwnState() {
        crawler.stopCrawlGracefully("test");
        verify(states).stop("test", "Manually stopped by user");
        verifyNoInteractions(redis, producer, search);
    }

    @Test
    void unknownCrawlReturnsNotFoundInsteadOfEmptySuccess() {
        when(states.status("missing")).thenReturn(null);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> crawler.getCrawlInfo("missing"));
        assertEquals(404, error.getStatus().value());
    }

    @Test
    void failedInitialSubmissionMarksTheCrawlAsFailed() throws Exception {
        when(states.queue(any())).thenReturn(true);
        doThrow(new IOException("Broker unavailable")).when(producer).send(any());
        CrawlerRequest request = new CrawlerRequest();
        request.setUrl("https://example.com/page");
        request.setMaxSeconds(20);
        assertThrows(IOException.class, () -> crawler.crawl("test", request));
        verify(states).fail("test", "Could not confirm submission to Kafka");
    }
}