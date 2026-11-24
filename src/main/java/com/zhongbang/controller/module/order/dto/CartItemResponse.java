package com.zhongbang.controller.module.order.dto;

import com.zhongbang.controller.common.enums.DeliveryMethod;

public record CartItemResponse(
        Long id,
        Long skuId,
        String productName,
        String skuName,
        Long unitPrice,
        Integer quantity,
        DeliveryMethod deliveryMethod,
        boolean needInstall,
        Long lineAmount
) {
}
