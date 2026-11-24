package com.zhongbang.controller.module.auth.controller;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.module.auth.dto.LoginRequest;
import com.zhongbang.controller.module.auth.dto.StaffLoginRequest;
import com.zhongbang.controller.module.auth.dto.StaffLoginResponse;
import com.zhongbang.controller.module.auth.dto.LoginResponse;
import com.zhongbang.controller.module.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/phone")
    public ApiResponse<Void> bindPhone() {
        // Skeleton placeholder — WxJava phone decrypt + customer merge belongs here.
        throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号绑定尚未实现，请后续接入微信能力");
    }

    @PostMapping("/staff-login")
    public ApiResponse<StaffLoginResponse> staffLogin(@Valid @RequestBody StaffLoginRequest request) {
        return ApiResponse.ok(authService.staffLogin(request));
    }
}

