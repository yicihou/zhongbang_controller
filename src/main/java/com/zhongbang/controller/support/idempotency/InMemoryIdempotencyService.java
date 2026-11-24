package com.zhongbang.controller.support.idempotency;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单进程内的幂等表。用 {@code putIfAbsent} 保证并发下只有第一个请求占到坑。
 * 过期记录在每次 begin 时顺手删掉，没有后台线程。重启进程后记录消失。
 * 由 {@code InfrastructureAutoConfig} 在 Redis 关闭时创建，不标 {@code @Service}，避免和 Redis 版同时存在。
 */
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
