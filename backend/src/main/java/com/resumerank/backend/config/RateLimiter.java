package com.resumerank.backend.config;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RateLimiter {

    private final int limit;
    private final long windowSizeMillis;
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<Long>> registry = new ConcurrentHashMap<>();

    public RateLimiter(int limit, long windowSizeSeconds) {
        this.limit = limit;
        this.windowSizeMillis = windowSizeSeconds * 1000;
    }

    public boolean isAllowed(String key) {
        long now = Instant.now().toEpochMilli();
        ConcurrentLinkedQueue<Long> timestamps = registry.computeIfAbsent(key, k -> new ConcurrentLinkedQueue<>());
        
        long threshold = now - windowSizeMillis;
        while (!timestamps.isEmpty() && timestamps.peek() < threshold) {
            timestamps.poll();
        }

        if (timestamps.size() < limit) {
            timestamps.add(now);
            return true;
        }
        return false;
    }

    public long getRetryAfterSeconds(String key) {
        long now = Instant.now().toEpochMilli();
        ConcurrentLinkedQueue<Long> timestamps = registry.get(key);
        if (timestamps == null || timestamps.isEmpty()) {
            return 0;
        }
        Long oldest = timestamps.peek();
        if (oldest == null) {
            return 0;
        }
        long remainingMillis = (oldest + windowSizeMillis) - now;
        return Math.max(1, remainingMillis / 1000);
    }
}
