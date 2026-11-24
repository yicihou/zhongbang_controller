package com.zhongbang.controller.module.order;

import com.zhongbang.controller.common.enums.OrderStatus;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(OrderStatus.PENDING_PAYMENT, EnumSet.of(OrderStatus.PAID, OrderStatus.CANCELLED, OrderStatus.PENDING_PAYMENT));
        ALLOWED.put(OrderStatus.PAID, EnumSet.of(OrderStatus.FULFILLING, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.FULFILLING, EnumSet.of(OrderStatus.COMPLETED, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    private OrderStateMachine() {
    }

    public static void assertTransition(OrderStatus from, OrderStatus to) {
        if (from == to) {
            return;
        }
        Set<OrderStatus> next = ALLOWED.getOrDefault(from, Set.of());
        if (!next.contains(to)) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE,
                    "订单状态不允许从 " + from + " 变更为 " + to);
        }
    }
}
