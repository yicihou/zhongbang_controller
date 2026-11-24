package com.zhongbang.controller.support.lock;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

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
