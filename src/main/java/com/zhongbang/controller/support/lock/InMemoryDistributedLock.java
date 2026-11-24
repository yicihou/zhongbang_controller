package com.zhongbang.controller.support.lock;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryDistributedLock implements DistributedLock {

    private final Map<String, Instant> locks = new ConcurrentHashMap<>();

    @Override
    public synchronized boolean tryLock(String key, Duration ttl) {
        Instant now = Instant.now();
        locks.entrySet().removeIf(e -> e.getValue().isBefore(now));
        Instant expireAt = locks.get(key);
        if (expireAt != null && expireAt.isAfter(now)) {
            return false;
        }
        locks.put(key, now.plus(ttl));
        return true;
    }

    @Override
    public synchronized void unlock(String key) {
        locks.remove(key);
    }
}
