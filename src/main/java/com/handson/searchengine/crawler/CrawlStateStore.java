package com.handson.searchengine.crawler;

import com.handson.searchengine.model.CrawlStatus;
import com.handson.searchengine.model.CrawlerRecord;
import com.handson.searchengine.model.StopReason;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class CrawlStateStore {
    private static final String ADMISSION_KEY = "crawl:admission";
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List> script;

    public CrawlStateStore(StringRedisTemplate redis) {
        this.redis = redis;
        this.script = new DefaultRedisScript<>();
        this.script.setLocation(new ClassPathResource("crawl-state.lua"));
        this.script.setResultType(List.class);
    }

    public boolean admit() {
        return admit(ADMISSION_KEY);
    }

    boolean admit(String admissionKey) {
        return succeeded(execute(admissionKey, "admit"));
    }

    public void initialize(CrawlerRecord record) {
        if (!changed(record.getCrawlId(), "init",
                Long.toString(record.getMaxTime()),
                Integer.toString(record.getMaxUrls()),
                Integer.toString(record.getMaxDistance()))) {
            throw new IllegalStateException("Crawl ID already exists");
        }
    }

    public boolean queue(CrawlerRecord record) {
        return changed(record.getCrawlId(), "queue", record.getUrl(),
                Integer.toString(record.getDistance()));
    }

    public boolean claim(CrawlerRecord record) {
        return changed(record.getCrawlId(), "claim", record.getUrl());
    }

    public void finish(CrawlerRecord record) {
        changed(record.getCrawlId(), "finish", record.getUrl(),
                Integer.toString(record.getDistance()));
    }

    public void skip(CrawlerRecord record) {
        changed(record.getCrawlId(), "skip", record.getUrl());
    }

    public void stop(String crawlId, String message) {
        changed(crawlId, "stop", message);
    }

    public void fail(String crawlId, String message) {
        changed(crawlId, "error", message);
    }

    public CrawlStatus status(String crawlId) {
        List<?> result = execute(key(crawlId), "status");
        if (result.isEmpty()) return null;
        String reason = result.get(3).toString();
        CrawlStatus status = CrawlStatus.of(
                Integer.parseInt(result.get(0).toString()),
                Long.parseLong(result.get(1).toString()),
                Integer.parseInt(result.get(2).toString()),
                reason.isEmpty() ? null : StopReason.valueOf(reason));
        String message = result.get(4).toString();
        status.setErrorMessage(message.isEmpty() ? null : message);
        status.setLastModifiedMillis(Long.parseLong(result.get(5).toString()));
        return status;
    }

    private boolean changed(String id, String operation, String... values) {
        return succeeded(execute(key(id), operation, values));
    }

    private boolean succeeded(List<?> result) {
        return !result.isEmpty() && "1".equals(result.get(0).toString());
    }

    private List<?> execute(String redisKey, String operation, String... values) {
        List<String> args = new ArrayList<>();
        args.add(operation);
        args.add(Long.toString(System.currentTimeMillis()));
        Collections.addAll(args, values);
        List<?> result = redis.execute(script,
                Collections.singletonList(redisKey), args.toArray());
        if (result == null) throw new IllegalStateException("Redis returned no crawl state");
        return result;
    }

    static String key(String id) {
        return "crawl:{" + id + "}:state";
    }
}