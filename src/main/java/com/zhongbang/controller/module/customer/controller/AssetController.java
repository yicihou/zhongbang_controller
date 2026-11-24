package com.zhongbang.controller.module.customer.controller;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.module.customer.dto.AssetBindRequest;
import com.zhongbang.controller.module.customer.dto.AssetResponse;
import com.zhongbang.controller.module.customer.service.AssetService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/assets")
@PreAuthorize("hasRole('CUSTOMER')")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping
    public ApiResponse<List<AssetResponse>> listMine() {
        return ApiResponse.ok(assetService.listMine());
    }

    @PostMapping("/bind")
    public ApiResponse<AssetResponse> bind(@Valid @RequestBody AssetBindRequest request) {
        return ApiResponse.ok(assetService.bind(request));
    }
}
