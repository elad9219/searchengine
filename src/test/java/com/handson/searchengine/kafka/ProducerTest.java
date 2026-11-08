package com.handson.searchengine.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.handson.searchengine.model.CrawlerRecord;
import com.handson.searchengine.model.CrawlerRequest;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProducerTest {
    @Test
    void waitsForBrokerAcknowledgementAndUsesCrawlIdAsKey() throws Exception {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> ack = CompletableFuture.completedFuture(null);
        when(kafka.send(eq(Producer.APP_TOPIC), eq("test"), anyString())).thenReturn(ack);
        CrawlerRecord record = CrawlerRecord.of(new CrawlerRequest()).withCrawlId("test");
        new Producer(new ObjectMapper(), kafka).send(record);
        verify(kafka).send(eq(Producer.APP_TOPIC), eq("test"), contains("\"crawlId\":\"test\""));
    }

    @Test
    void brokerFailureIsNotReportedAsSuccessfulSubmission() {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> ack =
                CompletableFuture.failedFuture(new IOException("Broker failure"));
        when(kafka.send(eq(Producer.APP_TOPIC), nullable(String.class), anyString())).thenReturn(ack);
        assertThrows(IOException.class, () -> new Producer(new ObjectMapper(), kafka).send("message"));
    }
}