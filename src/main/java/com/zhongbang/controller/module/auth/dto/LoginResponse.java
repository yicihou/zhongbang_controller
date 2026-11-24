package com.zhongbang.controller.module.auth.dto;

public record LoginResponse(
        String token,
        long expiresIn,
        boolean hasMobile,
        CustomerBrief customer
) {
    public record CustomerBrief(
            Long id,
            String nickname,
            String avatar,
            String mobile,
            int level,
            String levelText
    ) {
    }
}
