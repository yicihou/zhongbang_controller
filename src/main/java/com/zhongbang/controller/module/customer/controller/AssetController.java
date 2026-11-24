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

/**
 * 当前客户的设备档案。小程序设备页调用这两个路径。
 *
 * <p>{@code @PreAuthorize} 在方法执行前检查角色。{@code hasRole('CUSTOMER')} 实际比对的是
 * {@code ROLE_CUSTOMER}。员工或师傅令牌会得到 403；没登录在过滤器链就被 401，到不了这里。
 */
@RestController
@RequestMapping("/api/assets")
@PreAuthorize("hasRole('CUSTOMER')")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    /** 只返回令牌里那个客户的设备，按 id 倒序。没有分页。 */
    @GetMapping
    public ApiResponse<List<AssetResponse>> listMine() {
        return ApiResponse.ok(assetService.listMine());
    }

    /** 按序列号绑定。请求体只有 sn，且不能空白。 */
    @PostMapping("/bind")
    public ApiResponse<AssetResponse> bind(@Valid @RequestBody AssetBindRequest request) {
        return ApiResponse.ok(assetService.bind(request));
    }
}
