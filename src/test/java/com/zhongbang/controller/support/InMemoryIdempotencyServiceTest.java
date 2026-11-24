package com.zhongbang.controller.support;

import com.zhongbang.controller.support.idempotency.InMemoryIdempotencyService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 不启动 Spring，直接 new 内存幂等服务。
 * 断言：第一次 begin 是空（表示占坑成功），第二次读到 PENDING，complete 之后再 begin 读到结果标记。
 */
class InMemoryIdempotencyServiceTest {

    @Test
    void beginIsIdempotent() {
        InMemoryIdempotencyService service = new InMemoryIdempotencyService();
        assertThat(service.begin("r1", Duration.ofMinutes(10))).isEmpty();
        Optional<String> second = service.begin("r1", Duration.ofMinutes(10));
        assertThat(second).contains("PENDING");
        service.complete("r1", "ORDER:1", Duration.ofMinutes(10));
        assertThat(service.begin("r1", Duration.ofMinutes(10))).contains("ORDER:1");
    }
}
