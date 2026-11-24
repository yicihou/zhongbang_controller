package com.zhongbang.controller.support.idempotency;

import java.time.Duration;
import java.util.Optional;

/**
 * 用 requestId 挡住重复提交的钩子。Redis 打开时用 Redis 实现，否则用内存实现。
 * 没有任何下单代码调用它，接口本身可用，{@code InMemoryIdempotencyServiceTest} 覆盖了内存版。
 */
public interface IdempotencyService {

    /**
     * 第一次见到这个 requestId 时占坑并返回 empty，值记为 {@code PENDING}。
     * 已经存在则返回上次写下的标记（还在处理中就是 PENDING，完成后是调用方传入的结果标记）。
     *
     * @param ttl 这条记录多久后可以丢掉
     */
    Optional<String> begin(String requestId, Duration ttl);

    /** 业务成功后把占坑改成结果标记，供重复请求读回。resultMarker 的格式由调用方自己约定。 */
    void complete(String requestId, String resultMarker, Duration ttl);
}
