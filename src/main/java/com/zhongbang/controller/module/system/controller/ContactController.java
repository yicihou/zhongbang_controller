package com.zhongbang.controller.module.system.controller;

import com.zhongbang.controller.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/contact")
public class ContactController {

    @GetMapping("/admin")
    public ApiResponse<Map<String, String>> adminContact() {
        // TODO: load from zhongbang.notify / store config
        return ApiResponse.ok(Map.of(
                "wechat", "zhongbang-admin",
                "mobile", "400-000-0000"
        ));
    }
}
