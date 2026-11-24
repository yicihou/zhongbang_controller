package com.zhongbang.controller.support.idempotency;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis 版幂等。键是 {@code idem:} 加 requestId，用 SETNX（setIfAbsent）占坑并带过期时间。
 * 占坑失败时再 GET 一次；若 key 刚好过期导致读不到，返回字面量 {@code DUPLICATE}，而不是 empty。
 * 只在 {@code zhongbang.redis.enabled=true} 时注册。
 */
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
