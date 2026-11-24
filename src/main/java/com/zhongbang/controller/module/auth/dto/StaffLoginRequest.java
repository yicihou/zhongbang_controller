package com.zhongbang.controller.module.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record StaffLoginRequest(
        @NotBlank String username
) {
}
