package com.example.property.config;

public record WorkerConfig(
        String apiKey,
        int concurrency,
        int maxMessages,
        int visibilityTimeout,
        double permitsPerSecond) {

    public static WorkerConfig fromEnvironment() {
        String key = required("INFRAI_API_KEY");
        return new WorkerConfig(
                key,
                integer("WORKER_CONCURRENCY", 4),
                integer("MAX_MESSAGES", 8),
                integer("VISIBILITY_TIMEOUT", 60),
                decimal("PERMITS_PER_SECOND", 2.0));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
        return value;
    }

    private static int integer(String name, int fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : Integer.parseInt(value);
    }

    private static double decimal(String name, double fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : Double.parseDouble(value);
    }
}
