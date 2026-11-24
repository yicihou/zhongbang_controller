package com.zhongbang.controller.support.lock;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内锁。方法加了 synchronized，所以单机并发是安全的，多实例部署时各锁各的，起不到互斥。
 * Redis 关闭时由自动配置注册。
 */
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
