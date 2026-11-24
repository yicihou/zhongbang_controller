package com.zhongbang.controller.module.payment.channel;

import com.zhongbang.controller.common.enums.PaymentChannel;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class PaymentChannelRegistry {

    private final Map<PaymentChannel, PaymentChannelHandler> handlers = new EnumMap<>(PaymentChannel.class);

    public PaymentChannelRegistry(List<PaymentChannelHandler> handlerList) {
        for (PaymentChannelHandler handler : handlerList) {
            handlers.put(handler.channel(), handler);
        }
    }

    public PaymentChannelHandler require(PaymentChannel channel) {
        PaymentChannelHandler handler = handlers.get(channel);
        if (handler == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的支付渠道: " + channel);
        }
        return handler;
    }
}
