package com.example.property.service;

import com.example.property.client.InfraiQueueClient;
import com.example.property.domain.JobDecision;
import com.example.property.domain.PropertyJob;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class PropertyQueueWorker {
    private final InfraiQueueClient queue;
    private final PropertyJobPolicy policy;
    private final PermitRateLimiter limiter;
    private final int concurrency;

    public PropertyQueueWorker(InfraiQueueClient queue, PropertyJobPolicy policy,
                               PermitRateLimiter limiter, int concurrency) {
        this.queue = queue;
        this.policy = policy;
        this.limiter = limiter;
        this.concurrency = concurrency;
    }

    public int runBatch(int maxMessages, int visibilityTimeout) {
        List<Map<String, Object>> messages = queue.consume(maxMessages, visibilityTimeout);
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        for (Map<String, Object> message : messages) pool.submit(() -> process(message));
        pool.shutdown();
        try {
            if (!pool.awaitTermination(visibilityTimeout, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Batch exceeded visibility window");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Batch interrupted", exception);
        }
        return messages.size();
    }

    @SuppressWarnings("unchecked")
    private void process(Map<String, Object> message) {
        limiter.acquire();
        PropertyJob job = PropertyJob.fromPayload((Map<String, Object>) message.get("payload"));
        JobDecision decision = policy.decide(job);
        System.out.printf("property=%s subject=%s action=%s serviceLevel=%s audit=%s%n",
                job.propertyId(), job.subjectId(), decision.action(), decision.serviceLevel(),
                decision.requiresAuditRecord());
        queue.ack(String.valueOf(message.get("message_id")));
    }
}
