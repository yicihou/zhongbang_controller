package com.zhongbang.controller.module.payment.channel;

import com.zhongbang.controller.common.enums.PaymentChannel;
import com.zhongbang.controller.module.payment.entity.Payment;

import java.util.List;
import java.util.Map;

public interface PaymentChannelHandler {

    PaymentChannel channel();

    /** WeChat / online prepay. Offline returns empty map. */
    Map<String, Object> prepay(Payment payment);

    /** Admin records offline receipt. */
    void confirmOffline(Payment payment, Long operatorId, String receiptRemark, List<String> voucherUrls);
}
