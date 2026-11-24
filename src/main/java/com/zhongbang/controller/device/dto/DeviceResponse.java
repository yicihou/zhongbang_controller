package com.zhongbang.controller.device.dto;

import com.zhongbang.controller.device.domain.Device;
import com.zhongbang.controller.device.domain.DeviceStatus;

import java.time.Instant;

public record DeviceResponse(
        Long id,
        String code,
        String name,
        DeviceStatus status,
        String description,
        Instant createdAt,
        Instant updatedAt
) {

    public static DeviceResponse from(Device device) {
        return new DeviceResponse(
                device.getId(),
                device.getCode(),
                device.getName(),
                device.getStatus(),
                device.getDescription(),
                device.getCreatedAt(),
                device.getUpdatedAt()
        );
    }
}
