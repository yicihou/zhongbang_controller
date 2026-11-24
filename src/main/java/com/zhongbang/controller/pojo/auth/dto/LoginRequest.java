package com.zhongbang.controller.pojo.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /api/auth/login} 的请求体，只有一个字段。
 * {@code @NotBlank} 拒绝 null、空串和纯空白。这不是微信的真实校验，任何非空字符串都能登录。
 */
public record LoginRequest(@NotBlank String code) {
}
