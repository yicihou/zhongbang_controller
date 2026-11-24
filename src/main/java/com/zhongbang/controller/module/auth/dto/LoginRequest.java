package com.zhongbang.controller.module.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String code) {
}
