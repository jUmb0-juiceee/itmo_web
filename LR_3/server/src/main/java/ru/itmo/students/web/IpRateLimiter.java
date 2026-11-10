package ru.itmo.students.web;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Sliding window with a monotonic clock; intended for this single-process lab server. */
public class IpRateLimiter {
    private final int maxRequests;
    private final long windowNanos;
    private final LongSupplier clock;
    private final Map<String, ArrayDeque<Long>> requests = new HashMap<>();
    private long lastCleanup;

    public IpRateLimiter(int maxRequests, Duration window, LongSupplier clock) {
        this.maxRequests = maxRequests;
        this.windowNanos = window.toNanos();
        this.clock = clock;
        this.lastCleanup = clock.getAsLong();
    }

    /** Returns 0 when allowed, otherwise the minimum Retry-After in whole seconds. */
    public synchronized long check(String address) {
        long now = clock.getAsLong();
        if (now - lastCleanup >= windowNanos) {
            requests.entrySet().removeIf(entry -> entry.getValue().isEmpty()
                    || now - entry.getValue().peekLast() >= windowNanos);
            lastCleanup = now;
        }
        var times = requests.computeIfAbsent(address, ignored -> new ArrayDeque<>());
        while (!times.isEmpty() && now - times.peekFirst() >= windowNanos) times.removeFirst();
        if (times.size() >= maxRequests) {
            long remaining = windowNanos - (now - times.peekFirst());
            return Math.max(1, (remaining + 999_999_999L) / 1_000_000_000L);
        }
        times.addLast(now);
        return 0;
    }
}
