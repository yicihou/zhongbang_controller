package com.zhongbang.controller.module.order.dto;

import com.zhongbang.controller.common.enums.DeliveryMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record CreateOrderRequest(
        @NotBlank String requestId,
        Long addressId,
        @NotEmpty @Valid List<Item> items,
        String remark
) {
    public record Item(
            @NotNull Long skuId,
            @NotNull @Min(1) Integer quantity,
            @NotNull DeliveryMethod deliveryMethod,
            LocalDate installDate,
            String installSlot,
            Long technicianId
    ) {
    }
}
