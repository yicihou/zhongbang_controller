package com.zhongbang.controller.support.idempotency;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryIdempotencyService implements IdempotencyService {

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public Optional<String> begin(String requestId, Duration ttl) {
        purgeExpired();
        Entry existing = store.putIfAbsent(requestId, new Entry("PENDING", Instant.now().plus(ttl)));
        if (existing == null) {
            return Optional.empty();
        }
        return Optional.of(existing.value());
    }

    @Override
    public void complete(String requestId, String resultMarker, Duration ttl) {
        store.put(requestId, new Entry(resultMarker, Instant.now().plus(ttl)));
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        store.entrySet().removeIf(e -> e.getValue().expireAt().isBefore(now));
    }

    private record Entry(String value, Instant expireAt) {
    }
}
