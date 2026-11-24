package com.zhongbang.controller.module.customer.dto;

import com.zhongbang.controller.module.customer.entity.CustomerAsset;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public record AssetResponse(
        Long id,
        String model,
        String sn,
        String buyDate,
        String warrantyEnd,
        String warrantyStatus,
        String warrantyText,
        String lastServiceAt
) {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    public static AssetResponse from(CustomerAsset asset) {
        LocalDate end = asset.getWarrantyEnd();
        String status;
        String text;
        if (end == null) {
            status = "UNKNOWN";
            text = "保修状态未知";
        } else if (!end.isBefore(LocalDate.now())) {
            status = "IN";
            text = "保内，至 " + end.format(DATE);
        } else {
            status = "OUT";
            text = "已过保";
        }
        return new AssetResponse(
                asset.getId(),
                asset.getModel(),
                asset.getSn(),
                asset.getBuyDate() == null ? "" : asset.getBuyDate().format(DATE),
                end == null ? "" : end.format(DATE),
                status,
                text,
                asset.getLastServiceAt() == null ? "" : asset.getLastServiceAt().toString()
        );
    }
}
