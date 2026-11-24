package com.zhongbang.controller.module.aftersale;

import com.zhongbang.controller.common.enums.RepairStatus;
import com.zhongbang.controller.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepairStateMachineTest {

    @Test
    void allowsSubmittedToQuoted() {
        assertThatCode(() -> RepairStateMachine.assertTransition(RepairStatus.SUBMITTED, RepairStatus.QUOTED))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDoneToQuoted() {
        assertThatThrownBy(() -> RepairStateMachine.assertTransition(RepairStatus.DONE, RepairStatus.QUOTED))
                .isInstanceOf(BusinessException.class);
    }
}
