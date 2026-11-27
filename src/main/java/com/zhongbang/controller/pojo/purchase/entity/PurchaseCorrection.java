package com.zhongbang.controller.pojo.purchase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("purchase_correction")
public record PurchaseCorrection(
        @TableId(type = IdType.INPUT) Long id,
        Long purchaseId,
        String reason,
        Long operatorId,
        String operatorName,
        String beforeJson,
        String afterJson,
        LocalDateTime createdAt
) {
}
