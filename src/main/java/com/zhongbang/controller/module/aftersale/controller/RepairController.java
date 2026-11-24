package com.zhongbang.controller.module.aftersale.controller;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.module.aftersale.dto.CreateRepairRequest;
import com.zhongbang.controller.module.aftersale.service.RepairService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/repairs")
@PreAuthorize("hasRole('CUSTOMER')")
public class RepairController {

    private final RepairService repairService;

    public RepairController(RepairService repairService) {
        this.repairService = repairService;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody CreateRepairRequest request) {
        return ApiResponse.ok(repairService.create(request));
    }

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(repairService.listMine(page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(repairService.detailMine(id));
    }

    @PostMapping("/{id}/quote/confirm")
    public ApiResponse<Map<String, Object>> confirmQuote(@PathVariable Long id) {
        return ApiResponse.ok(repairService.confirmQuote(id));
    }
}
