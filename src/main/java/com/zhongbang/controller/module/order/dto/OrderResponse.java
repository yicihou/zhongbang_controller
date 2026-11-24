package com.zhongbang.controller.module.order.dto;

import com.zhongbang.controller.common.enums.DeliveryMethod;
import com.zhongbang.controller.common.enums.OrderStatus;
import com.zhongbang.controller.common.enums.PaymentStatus;
import com.zhongbang.controller.common.enums.PaymentType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderNo,
        OrderStatus status,
        Long goodsAmount,
        Long installAmount,
        Long freightAmount,
        Long adjustAmount,
        Long payableAmount,
        Long paidAmount,
        String remark,
        LocalDateTime createdAt,
        List<Item> items,
        List<PaymentBrief> payments
) {
    public record Item(
            Long id,
            Long skuId,
            String productName,
            String skuName,
            Integer quantity,
            Long unitPrice,
            Long lineAmount,
            DeliveryMethod deliveryMethod,
            Long installFee,
            LocalDate installDate,
            String installSlot,
            Long technicianId
    ) {
    }

    public record PaymentBrief(
            Long id,
            String paymentNo,
            PaymentType type,
            String title,
            Long amount,
            PaymentStatus status
    ) {
    }
}
