package com.zhongbang.controller.support.idempotency;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

public class RedisIdempotencyService implements IdempotencyService {

    private static final String PREFIX = "idem:";

    private final StringRedisTemplate redisTemplate;

    public RedisIdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<String> begin(String requestId, Duration ttl) {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(PREFIX + requestId, "PENDING", ttl);
        if (Boolean.TRUE.equals(ok)) {
            return Optional.empty();
        }
        String existing = redisTemplate.opsForValue().get(PREFIX + requestId);
        return Optional.ofNullable(existing == null ? "DUPLICATE" : existing);
    }

    @Override
    public void complete(String requestId, String resultMarker, Duration ttl) {
        redisTemplate.opsForValue().set(PREFIX + requestId, resultMarker, ttl);
    }
}
