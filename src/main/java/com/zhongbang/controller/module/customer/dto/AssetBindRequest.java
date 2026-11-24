package com.zhongbang.controller.module.customer.dto;

import jakarta.validation.constraints.NotBlank;

public record AssetBindRequest(
        @NotBlank String sn
) {
}
