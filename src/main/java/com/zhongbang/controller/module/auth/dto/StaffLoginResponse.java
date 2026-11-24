package com.zhongbang.controller.module.auth.dto;

public record StaffLoginResponse(
        String token,
        long expiresIn,
        Long userId,
        String username,
        String role
) {
}
