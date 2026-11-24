package com.zhongbang.controller.module.admin.controller;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.module.payment.service.AdminPaymentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/payments")
@PreAuthorize("hasRole('STAFF')")
public class AdminPaymentController {

    private final AdminPaymentService adminPaymentService;

    public AdminPaymentController(AdminPaymentService adminPaymentService) {
        this.adminPaymentService = adminPaymentService;
    }

    @PatchMapping("/{id}")
    public ApiResponse<Void> patch(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        // TODO(P1b): update WAIT payment amount/title
        throw new BusinessException(ErrorCode.BAD_REQUEST, "改应付金额尚未实现（P1b）");
    }

    @PostMapping("/{id}/offline-confirm")
    public ApiResponse<Map<String, Object>> offlineConfirm(
            @PathVariable Long id,
            @RequestBody(required = false) OfflineConfirmRequest body
    ) {
        String remark = body == null ? null : body.remark();
        List<String> vouchers = body == null ? null : body.voucherUrls();
        return ApiResponse.ok(adminPaymentService.offlineConfirm(id, remark, vouchers));
    }

    public record OfflineConfirmRequest(String remark, List<String> voucherUrls) {
    }
}
