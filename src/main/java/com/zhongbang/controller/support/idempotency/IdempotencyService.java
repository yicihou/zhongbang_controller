package com.zhongbang.controller.support.idempotency;

import java.time.Duration;
import java.util.Optional;

/**
 * requestId idempotency hook (docs §4.2). Redis-backed when enabled, otherwise in-memory for local/dev.
 */
public interface IdempotencyService {

    /**
     * @return empty if this is the first seen requestId; previous payload marker if duplicate
     */
    Optional<String> begin(String requestId, Duration ttl);

    void complete(String requestId, String resultMarker, Duration ttl);
}
