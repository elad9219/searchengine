package com.handson.searchengine.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.handson.searchengine.model.CrawlerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class Producer {
    public static final String APP_TOPIC = "searchengine";

    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public Producer(ObjectMapper objectMapper, KafkaTemplate<String, String> kafkaTemplate) {
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(Object message) throws IOException, InterruptedException {
        String key = message instanceof CrawlerRecord
                ? ((CrawlerRecord) message).getCrawlId() : null;
        try {
            kafkaTemplate.send(APP_TOPIC, key, objectMapper.writeValueAsString(message))
                    .get(10, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException e) {
            throw new IOException("Kafka did not confirm the crawl message", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }
}