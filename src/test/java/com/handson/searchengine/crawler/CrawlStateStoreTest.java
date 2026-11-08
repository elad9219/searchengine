package com.handson.searchengine.crawler;

import com.handson.searchengine.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "SEARCHENGINE_REDIS_TEST", matches = "true")
class CrawlStateStoreTest {
    private static LettuceConnectionFactory connection;
    private static StringRedisTemplate redis;
    private CrawlStateStore store;
    private CrawlerRecord root;

    @BeforeAll
    static void connect() {
        int port = Integer.getInteger("searchengine.redis-test-port", 16379);
        connection = new LettuceConnectionFactory("127.0.0.1", port);
        connection.afterPropertiesSet();
        redis = new StringRedisTemplate(connection);
    }

    @AfterAll
    static void disconnect() {
        connection.destroy();
    }

    @BeforeEach
    void setup() {
        store = new CrawlStateStore(redis);
        CrawlerRequest request = new CrawlerRequest();
        request.setUrl("https://example.com/page");
        request.setMaxSeconds(20);
        request.setMaxDistance(1);
        request.setMaxUrls(3);
        root = CrawlerRecord.of(request).withCrawlId("test-" + UUID.randomUUID());
        store.initialize(root);
    }

    @AfterEach
    void cleanup() {
        redis.delete(CrawlStateStore.key(root.getCrawlId()));
    }

    @Test
    void queuingDoesNotCountAsAProcessedPage() {
        assertTrue(store.queue(root));
        assertEquals(0, store.status(root.getCrawlId()).getNumPages());
        assertFalse(store.queue(root));
    }

    @Test
    void concurrentClaimsAndFinishesCountAUrlOnce() throws Exception {
        store.queue(root);
        ExecutorService workers = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> attempts = new ArrayList<>();
            for (int i = 0; i < 20; i++) attempts.add(() -> store.claim(root));
            int claims = 0;
            for (Future<Boolean> result : workers.invokeAll(attempts)) {
                if (result.get()) claims++;
            }
            assertEquals(1, claims);
            store.finish(root);
            store.finish(root);
            assertEquals(1, store.status(root.getCrawlId()).getNumPages());
            assertEquals(StopReason.completed, store.status(root.getCrawlId()).getStopReason());
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void completionWaitsForAlreadyQueuedChildren() {
        store.queue(root);
        store.claim(root);
        CrawlerRecord child = CrawlerRecord.of(root).withUrl("https://example.com/child").withIncDistance();
        assertTrue(store.queue(child));
        store.finish(root);
        assertNull(store.status(root.getCrawlId()).getStopReason());
        assertTrue(store.claim(child));
        store.finish(child);
        assertEquals(2, store.status(root.getCrawlId()).getNumPages());
        assertEquals(StopReason.completed, store.status(root.getCrawlId()).getStopReason());
    }

    @Test
    void pageLimitIncludesReservedWorkWithoutInflatingVisitedCount() {
        store.queue(root);
        CrawlerRecord first = CrawlerRecord.of(root).withUrl("https://example.com/a");
        CrawlerRecord second = CrawlerRecord.of(root).withUrl("https://example.com/b");
        assertTrue(store.queue(first));
        assertTrue(store.queue(second));
        assertFalse(store.queue(CrawlerRecord.of(root).withUrl("https://example.com/c")));
        assertEquals(0, store.status(root.getCrawlId()).getNumPages());
        for (CrawlerRecord record : java.util.Arrays.asList(root, first, second)) {
            assertTrue(store.claim(record));
            store.finish(record);
        }
        assertEquals(3, store.status(root.getCrawlId()).getNumPages());
        assertEquals(StopReason.maxUrls, store.status(root.getCrawlId()).getStopReason());
    }

    @Test
    void depthLimitProducesATerminalReasonAfterWorkFinishes() {
        store.queue(root);
        store.claim(root);
        CrawlerRecord child = CrawlerRecord.of(root).withUrl("https://example.com/deep")
                .withIncDistance().withIncDistance();
        assertFalse(store.queue(child));
        store.finish(root);
        assertEquals(StopReason.maxDistance, store.status(root.getCrawlId()).getStopReason());
    }

    @Test
    void alreadyKnownUrlDoesNotFalselyTriggerTheDepthLimit() {
        store.queue(root);
        store.claim(root);
        assertFalse(store.queue(CrawlerRecord.of(root).withIncDistance().withIncDistance()));
        store.finish(root);
        assertEquals(StopReason.completed, store.status(root.getCrawlId()).getStopReason());
    }

    @Test
    void manualStopCannotBeOverwrittenByALateCompletion() {
        store.queue(root);
        store.claim(root);
        store.stop(root.getCrawlId(), "Stopped");
        store.finish(root);
        assertEquals(StopReason.userInitiated, store.status(root.getCrawlId()).getStopReason());
        assertFalse(store.claim(root));
    }

    @Test
    void stoppingOneCrawlLeavesAnotherCrawlRunning() {
        CrawlerRecord other = CrawlerRecord.of(root).withCrawlId("test-" + UUID.randomUUID());
        try {
            store.initialize(other);
            store.queue(other);
            store.stop(root.getCrawlId(), "Stopped");
            assertNull(store.status(other.getCrawlId()).getStopReason());
            assertTrue(store.claim(other));
            store.finish(other);
            assertEquals(StopReason.completed, store.status(other.getCrawlId()).getStopReason());
        } finally {
            redis.delete(CrawlStateStore.key(other.getCrawlId()));
        }
    }

    @Test
    void expiredDeadlineIsReportedWithoutAnotherKafkaMessage() {
        redis.opsForHash().put(CrawlStateStore.key(root.getCrawlId()), "deadline", "1");
        assertEquals(StopReason.timeout, store.status(root.getCrawlId()).getStopReason());
        assertFalse(store.queue(root));
    }

    @Test
    void initializationDoesNotOverwriteExistingData() {
        store.queue(root);
        assertThrows(IllegalStateException.class, () -> store.initialize(root));
        assertTrue(store.claim(root));
    }
}