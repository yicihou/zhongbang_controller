package com.zhongbang.controller.controller.payment;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.pojo.order.dto.OrderRequests;
import com.zhongbang.controller.pojo.order.vo.OrderVo;
import com.zhongbang.controller.pojo.payment.vo.ReceivableVo;
import com.zhongbang.controller.service.order.OrderService;
import com.zhongbang.controller.service.payment.PaymentQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端收款。线下登记可以只收一部分，差额会拆成标题带「（余款）」的新待付。
 * 待收款列表给「待收款」页用。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminPaymentController {

    private final OrderService orderService;
    private final PaymentQueryService paymentQueryService;

    public AdminPaymentController(OrderService orderService, PaymentQueryService paymentQueryService) {
        this.orderService = orderService;
        this.paymentQueryService = paymentQueryService;
    }

    @GetMapping("/receivables")
    public ApiResponse<PageResult<ReceivableVo>> receivables(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(paymentQueryService.receivables(page, size));
    }

    @PatchMapping("/payments/{id}")
    public ApiResponse<OrderVo.Payment> patch(@PathVariable Long id, @RequestBody OrderRequests.PatchPayment request) {
        return ApiResponse.ok(orderService.patchPayment(id, request));
    }

    /**
     * 登记一笔线下到账。method 用 BANK_TRANSFER / WECHAT_TRANSFER / ALIPAY / CASH / OTHER，
     * 也接受 bank、wechat、alipay、cash、other。凭证和备注可以不填。
     */
    @PostMapping("/payments/{id}/offline-confirm")
    public ApiResponse<OrderVo.Payment> offlineConfirm(@PathVariable Long id, @RequestBody OrderRequests.OfflineConfirm request) {
        return ApiResponse.ok(orderService.offlineConfirm(id, request));
    }
}
