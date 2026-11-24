package com.zhongbang.controller.module.payment.channel;

import com.zhongbang.controller.common.enums.PaymentChannel;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.module.payment.entity.Payment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Phase-2 stub: JSAPI prepay / notify not wired yet.
 * Keep behind {@link PaymentChannelHandler} so callers stay channel-agnostic.
 */
@Component
public class WxPayPaymentChannel implements PaymentChannelHandler {

    @Override
    public PaymentChannel channel() {
        return PaymentChannel.WXPAY;
    }

    @Override
    public Map<String, Object> prepay(Payment payment) {
        // TODO(P2): WxJava JSAPI unified order + return prepay package to mini-program
        throw new BusinessException(ErrorCode.PAY_FAILED, "微信支付尚未开通（WXPAY stub）");
    }

    @Override
    public void confirmOffline(Payment payment, Long operatorId, String receiptRemark, List<String> voucherUrls) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "WXPAY 渠道不支持线下登记");
    }
}
