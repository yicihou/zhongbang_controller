package com.zhongbang.controller.module.payment.channel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhongbang.controller.common.enums.PaymentChannel;
import com.zhongbang.controller.common.enums.PaymentStatus;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.module.payment.entity.Payment;
import com.zhongbang.controller.module.payment.mapper.PaymentMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class OfflinePaymentChannel implements PaymentChannelHandler {

    private final PaymentMapper paymentMapper;
    private final ObjectMapper objectMapper;

    public OfflinePaymentChannel(PaymentMapper paymentMapper, ObjectMapper objectMapper) {
        this.paymentMapper = paymentMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public PaymentChannel channel() {
        return PaymentChannel.OFFLINE;
    }

    @Override
    public Map<String, Object> prepay(Payment payment) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "线下支付无需预下单，请管理员登记收款");
    }

    @Override
    public void confirmOffline(Payment payment, Long operatorId, String receiptRemark, List<String> voucherUrls) {
        if (payment.getStatus() != PaymentStatus.WAIT) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "仅待付记录可登记线下收款");
        }
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setChannel(PaymentChannel.OFFLINE);
        payment.setOperatorId(operatorId);
        payment.setPaidAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());
        if (voucherUrls != null && !voucherUrls.isEmpty()) {
            try {
                payment.setVoucherUrlsJson(objectMapper.writeValueAsString(voucherUrls));
            } catch (JsonProcessingException e) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "凭证格式错误");
            }
        }
        if (receiptRemark != null && !receiptRemark.isBlank()) {
            String title = payment.getTitle() == null ? "" : payment.getTitle();
            payment.setTitle(title.isBlank() ? receiptRemark : title + " | " + receiptRemark);
        }
        paymentMapper.updateById(payment);
    }
}
