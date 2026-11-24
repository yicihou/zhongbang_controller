package com.zhongbang.controller.device.dto;

import com.zhongbang.controller.device.domain.DeviceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeviceUpdateRequest(
        @NotBlank @Size(max = 128) String name,
        @NotNull DeviceStatus status,
        @Size(max = 256) String description
) {
}
