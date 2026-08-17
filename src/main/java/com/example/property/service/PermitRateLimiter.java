package com.example.property.service;

public final class PermitRateLimiter {
    private final long intervalNanos;
    private long nextPermit;

    public PermitRateLimiter(double permitsPerSecond) {
        if (permitsPerSecond <= 0) throw new IllegalArgumentException("permitsPerSecond must be positive");
        this.intervalNanos = (long) (1_000_000_000L / permitsPerSecond);
    }

    public synchronized void acquire() {
        long now = System.nanoTime();
        long granted = Math.max(now, nextPermit);
        nextPermit = granted + intervalNanos;
        long wait = granted - now;
        if (wait <= 0) return;
        try {
            long millis = wait / 1_000_000L;
            int nanos = (int) (wait % 1_000_000L);
            Thread.sleep(millis, nanos);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Worker interrupted", exception);
        }
    }
}
