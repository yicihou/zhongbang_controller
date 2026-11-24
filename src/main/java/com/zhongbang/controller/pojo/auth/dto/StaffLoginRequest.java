package com.zhongbang.controller.pojo.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 管理端登录请求。账号密码都不能是空白。
 */
public record StaffLoginRequest(
        @NotBlank(message = "请填写账号") String username,
        @NotBlank(message = "请填写密码") String password
) {
}
