package com.zhongbang.controller.module.order.dto;

import com.zhongbang.controller.common.enums.DeliveryMethod;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartItemRequest(
        @NotNull Long skuId,
        @NotNull @Min(1) Integer quantity,
        DeliveryMethod deliveryMethod
) {
}
