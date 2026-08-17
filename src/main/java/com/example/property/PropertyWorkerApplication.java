package com.example.property;

import com.example.property.client.InfraiQueueClient;
import com.example.property.config.WorkerConfig;
import com.example.property.service.PermitRateLimiter;
import com.example.property.service.PropertyJobPolicy;
import com.example.property.service.PropertyQueueWorker;

import java.util.Map;

public final class PropertyWorkerApplication {
    public static void main(String[] args) {
        WorkerConfig config = WorkerConfig.fromEnvironment();
        InfraiQueueClient queue = new InfraiQueueClient(config.apiKey());
        queue.create();
        queue.publish(Map.of(
                "type", "maintenance_request",
                "propertyId", "BLDG-204",
                "subjectId", "REQ-9182",
                "urgent", true));

        PropertyQueueWorker worker = new PropertyQueueWorker(
                queue,
                new PropertyJobPolicy(),
                new PermitRateLimiter(config.permitsPerSecond()),
                config.concurrency());
        int processed = worker.runBatch(config.maxMessages(), config.visibilityTimeout());
        System.out.println("processed=" + processed);
    }
}
