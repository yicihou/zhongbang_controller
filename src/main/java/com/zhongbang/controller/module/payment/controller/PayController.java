package com.zhongbang.controller.module.payment.controller;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/pay")
public class PayController {

    @PostMapping("/offline/intent")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<Map<String, Object>> offlineIntent(@RequestBody Map<String, Object> body) {
        // Optional P1a: customer marks "will transfer offline" — no state change yet
        return ApiResponse.ok(Map.of("accepted", true, "channel", "OFFLINE"));
    }

    @PostMapping("/prepay")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<Map<String, Object>> prepay(@RequestBody Map<String, Object> body) {
        // TODO(P2): load WAIT payment by id, then channelRegistry.require(WXPAY).prepay(payment)
        throw new BusinessException(ErrorCode.PAY_FAILED, "微信支付尚未开通（WXPAY stub）");
    }

    @PostMapping("/notify")
    public String notifyWx(@RequestBody String body) {
        // TODO(P2): verify WeChat notify signature and mark payment SUCCESS
        return "SUCCESS";
    }
}
