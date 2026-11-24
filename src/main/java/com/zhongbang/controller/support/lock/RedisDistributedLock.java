package com.zhongbang.controller.support.lock;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Redis 锁：{@code lock:} 加 key，SETNX 并设置过期。解锁是直接 DEL，不比较加锁时写入的值，
 * 因此过期后被别人重新加锁时，旧持有者仍可能把新锁删掉。这是占位实现，不是 Redlock。
 */
public class RedisDistributedLock implements DistributedLock {

    private static final String PREFIX = "lock:";

    private final StringRedisTemplate redisTemplate;

    public RedisDistributedLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(String key, Duration ttl) {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(PREFIX + key, "1", ttl);
        return Boolean.TRUE.equals(ok);
    }

    @Override
    public void unlock(String key) {
        redisTemplate.delete(PREFIX + key);
    }
}
