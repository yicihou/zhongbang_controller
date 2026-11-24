package com.zhongbang.controller.pojo.customer.dto;

import com.zhongbang.controller.pojo.customer.entity.CustomerAsset;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 返回给前端的设备卡片。日期用 ISO 本地日期（yyyy-MM-dd）。
 * {@code warrantyStatus}：没有保修截止日是 UNKNOWN；截止日不早于今天是 IN；否则 OUT。
 * 比较用的是服务器当天，不是客户时区。
 */
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
