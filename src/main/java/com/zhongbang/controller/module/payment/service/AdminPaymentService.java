package com.zhongbang.controller.module.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhongbang.controller.common.enums.BizType;
import com.zhongbang.controller.common.enums.OrderStatus;
import com.zhongbang.controller.common.enums.PaymentChannel;
import com.zhongbang.controller.common.enums.PaymentStatus;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.module.order.OrderStateMachine;
import com.zhongbang.controller.module.order.entity.SalesOrder;
import com.zhongbang.controller.module.order.mapper.SalesOrderMapper;
import com.zhongbang.controller.module.payment.PaymentStateMachine;
import com.zhongbang.controller.module.payment.channel.PaymentChannelRegistry;
import com.zhongbang.controller.module.payment.entity.Payment;
import com.zhongbang.controller.module.payment.mapper.PaymentMapper;
import com.zhongbang.controller.security.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Offline confirm is wired for P1a smoke; full receivables / stage edits land in P1b.
 */
@Service
public class AdminPaymentService {

    private final PaymentMapper paymentMapper;
    private final SalesOrderMapper salesOrderMapper;
    private final PaymentChannelRegistry channelRegistry;

    public AdminPaymentService(
            PaymentMapper paymentMapper,
            SalesOrderMapper salesOrderMapper,
            PaymentChannelRegistry channelRegistry
    ) {
        this.paymentMapper = paymentMapper;
        this.salesOrderMapper = salesOrderMapper;
        this.channelRegistry = channelRegistry;
    }

    @Transactional
    public Map<String, Object> offlineConfirm(Long paymentId, String remark, List<String> voucherUrls) {
        Payment payment = paymentMapper.selectById(paymentId);
        if (payment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "应付记录不存在");
        }
        PaymentStateMachine.assertTransition(payment.getStatus(), PaymentStatus.SUCCESS);
        Long operatorId = UserContext.require().getUserId();
        channelRegistry.require(PaymentChannel.OFFLINE)
                .confirmOffline(payment, operatorId, remark, voucherUrls);

        if (payment.getBizType() == BizType.ORDER && payment.getBizId() != null) {
            settleOrderIfCleared(payment.getBizId());
        }
        // TODO(P1c): settle repair ticket when all WAIT payments success

        Map<String, Object> result = new HashMap<>();
        result.put("paymentId", payment.getId());
        result.put("status", PaymentStatus.SUCCESS.name());
        return result;
    }

    private void settleOrderIfCleared(Long orderId) {
        SalesOrder order = salesOrderMapper.selectById(orderId);
        if (order == null) {
            return;
        }
        List<Payment> payments = paymentMapper.selectList(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getBizType, BizType.ORDER)
                .eq(Payment::getBizId, orderId));
        long paid = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .mapToLong(Payment::getAmount)
                .sum();
        boolean hasWait = payments.stream().anyMatch(p -> p.getStatus() == PaymentStatus.WAIT);
        order.setPaidAmount(paid);
        order.setBalanceAmount(Math.max(0, (order.getPayableAmount() == null ? 0 : order.getPayableAmount()) - paid));
        order.setUpdatedAt(LocalDateTime.now());
        if (!hasWait && paid >= (order.getPayableAmount() == null ? 0 : order.getPayableAmount())) {
            OrderStateMachine.assertTransition(order.getStatus(), OrderStatus.PAID);
            order.setStatus(OrderStatus.PAID);
            order.setPayTime(LocalDateTime.now());
        }
        salesOrderMapper.updateById(order);
    }
}
