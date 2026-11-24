package com.zhongbang.controller.module.aftersale;

import com.zhongbang.controller.common.enums.RepairStatus;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class RepairStateMachine {

    private static final Map<RepairStatus, Set<RepairStatus>> ALLOWED = new EnumMap<>(RepairStatus.class);

    static {
        ALLOWED.put(RepairStatus.SUBMITTED, EnumSet.of(RepairStatus.QUOTED, RepairStatus.CANCELLED));
        ALLOWED.put(RepairStatus.QUOTED, EnumSet.of(RepairStatus.PENDING_PAYMENT, RepairStatus.CONFIRMED, RepairStatus.CANCELLED));
        ALLOWED.put(RepairStatus.PENDING_PAYMENT, EnumSet.of(RepairStatus.PAID, RepairStatus.CANCELLED));
        ALLOWED.put(RepairStatus.PAID, EnumSet.of(RepairStatus.IN_SERVICE));
        ALLOWED.put(RepairStatus.CONFIRMED, EnumSet.of(RepairStatus.IN_SERVICE));
        ALLOWED.put(RepairStatus.IN_SERVICE, EnumSet.of(RepairStatus.DONE));
        ALLOWED.put(RepairStatus.DONE, EnumSet.noneOf(RepairStatus.class));
        ALLOWED.put(RepairStatus.CANCELLED, EnumSet.noneOf(RepairStatus.class));
    }

    private RepairStateMachine() {
    }

    public static void assertTransition(RepairStatus from, RepairStatus to) {
        if (from == to) {
            return;
        }
        Set<RepairStatus> next = ALLOWED.getOrDefault(from, Set.of());
        if (!next.contains(to)) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE,
                    "报修单状态不允许从 " + from + " 变更为 " + to);
        }
    }
}
