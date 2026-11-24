package com.zhongbang.controller.controller.system;

import com.zhongbang.controller.common.api.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * 探活接口，公开。{@code @Value} 把 yml 里的 {@code spring.application.name} 注入进来，
 * 用来在响应里标明是哪个服务，不访问数据库。
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final String applicationName;

    public SystemController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    /** data 含 service、status=UP、以及当前 UTC 时间的 ISO 字符串。 */
    @GetMapping("/ping")
    public ApiResponse<Map<String, Object>> ping() {
        return ApiResponse.ok(Map.of(
                "service", applicationName,
                "status", "UP",
                "timestamp", Instant.now().toString()
        ));
    }
}
