package com.zhongbang.controller.module.order;

import com.zhongbang.controller.common.enums.OrderStatus;
import com.zhongbang.controller.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateMachineTest {

    @Test
    void allowsPendingToPaidAndCancel() {
        assertThatCode(() -> OrderStateMachine.assertTransition(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID))
                .doesNotThrowAnyException();
        assertThatCode(() -> OrderStateMachine.assertTransition(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsCompletedToPaid() {
        assertThatThrownBy(() -> OrderStateMachine.assertTransition(OrderStatus.COMPLETED, OrderStatus.PAID))
                .isInstanceOf(BusinessException.class);
    }
}
