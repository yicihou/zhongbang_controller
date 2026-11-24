package com.zhongbang.controller.module.technician.controller;

import com.zhongbang.controller.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/technicians")
public class TechnicianController {

    @GetMapping("/available")
    public ApiResponse<List<Map<String, Object>>> available(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String slot
    ) {
        // TODO(P1b): query technician_schedule capacity
        return ApiResponse.ok(List.of());
    }
}
