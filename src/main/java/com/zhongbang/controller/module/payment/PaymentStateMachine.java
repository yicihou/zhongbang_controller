package com.zhongbang.controller.module.payment;

import com.zhongbang.controller.common.enums.PaymentStatus;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class PaymentStateMachine {

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED = new EnumMap<>(PaymentStatus.class);

    static {
        ALLOWED.put(PaymentStatus.WAIT, EnumSet.of(PaymentStatus.SUCCESS, PaymentStatus.CLOSED));
        ALLOWED.put(PaymentStatus.SUCCESS, EnumSet.of(PaymentStatus.REFUNDED));
        ALLOWED.put(PaymentStatus.CLOSED, EnumSet.noneOf(PaymentStatus.class));
        ALLOWED.put(PaymentStatus.REFUNDED, EnumSet.noneOf(PaymentStatus.class));
    }

    private PaymentStateMachine() {
    }

    public static void assertTransition(PaymentStatus from, PaymentStatus to) {
        if (from == to) {
            return;
        }
        Set<PaymentStatus> next = ALLOWED.getOrDefault(from, Set.of());
        if (!next.contains(to)) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE,
                    "支付状态不允许从 " + from + " 变更为 " + to);
        }
    }
}
