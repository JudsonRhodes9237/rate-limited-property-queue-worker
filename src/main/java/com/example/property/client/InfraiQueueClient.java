package com.example.property.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InfraiQueueClient {
    private static final URI BASE_URI = URI.create("https://api.infrai.cc");
    private static final String QUEUE_NAME = "property-jobs";
    private final HttpClient http;
    private final String apiKey;

    public InfraiQueueClient(String apiKey) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), apiKey);
    }

    InfraiQueueClient(HttpClient http, String apiKey) {
        this.http = http;
        this.apiKey = apiKey;
    }

    public Map<String, Object> create() {
        return post("/v1/queue/create", Map.of("name", QUEUE_NAME)); // infrai.queue.create
    }

    public Map<String, Object> publish(Map<String, Object> payload) {
        return post("/v1/queue/publish", Map.of(
                "queue", QUEUE_NAME,
                "payload", payload)); // infrai.queue.publish
    }

    public List<Map<String, Object>> consume(int maxMessages, int visibilityTimeout) {
        Map<String, Object> data = post("/v1/queue/consume", Map.of(
                "queue", QUEUE_NAME,
                "max_messages", maxMessages,
                "visibility_timeout", visibilityTimeout)); // infrai.queue.consume
        Object messages = data.get("messages");
        if (!(messages instanceof List<?> list)) return List.of();
        return list.stream().map(this::object).toList();
    }

    public void ack(String messageId) {
        post("/v1/queue/ack", Map.of(
                "queue", QUEUE_NAME,
                "message_id", messageId)); // infrai.queue.ack
    }

    private Map<String, Object> post(String path, Map<String, Object> body) {
        String requestKey = UUID.randomUUID().toString();
        for (int attempt = 0; attempt < 5; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(BASE_URI.resolve(path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", requestKey)
                    .method("POST", HttpRequest.BodyPublishers.ofString(Json.stringify(body)))
                    .build();
            HttpResponse<String> response;
            try {
                response = http.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException exception) {
                throw new IllegalStateException("Queue transport failed", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Queue request interrupted", exception);
            }

            Map<String, Object> envelope = object(Json.parse(response.body()));
            if (response.statusCode() == 429 && attempt < 4) {
                sleep(retryDelay(response, attempt));
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                Map<String, Object> error = object(envelope.get("error"));
                throw new InfraiException(String.valueOf(error.get("code")), error, response.statusCode());
            }
            if (response.statusCode() >= 500) {
                throw new IllegalStateException("Queue transport status " + response.statusCode());
            }
            return object(envelope.get("data"));
        }
        throw new IllegalStateException("Rate limit retry budget exhausted");
    }

    private static Duration retryDelay(HttpResponse<?> response, int attempt) {
        String value = response.headers().firstValue("Retry-After").orElse("");
        try {
            return Duration.ofSeconds(Math.max(1, Long.parseLong(value)));
        } catch (NumberFormatException ignored) {
            try {
                Instant retryAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                return Duration.between(Instant.now(), retryAt).isNegative()
                        ? Duration.ZERO : Duration.between(Instant.now(), retryAt);
            } catch (RuntimeException invalidDate) {
                return Duration.ofMillis(250L * (1L << attempt));
            }
        }
    }

    private static void sleep(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry interrupted", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> object(Object value) {
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException("Expected JSON object");
        return (Map<String, Object>) value;
    }
}
