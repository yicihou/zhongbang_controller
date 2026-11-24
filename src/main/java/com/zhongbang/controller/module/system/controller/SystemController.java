package com.zhongbang.controller.module.system.controller;

import com.zhongbang.controller.common.api.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final String applicationName;

    public SystemController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    @GetMapping("/ping")
    public ApiResponse<Map<String, Object>> ping() {
        return ApiResponse.ok(Map.of(
                "service", applicationName,
                "status", "UP",
                "timestamp", Instant.now().toString()
        ));
    }
}
