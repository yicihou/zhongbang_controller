package com.zhongbang.controller.module.admin.controller;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.common.enums.OrderStatus;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.module.order.dto.OrderResponse;
import com.zhongbang.controller.module.order.service.OrderService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('STAFF')")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ApiResponse<PageResult<OrderResponse>> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(orderService.adminList(status, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(orderService.adminDetail(id));
    }

    @PostMapping("/{id}/adjust-price")
    public ApiResponse<Void> adjustPrice(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        // TODO(P1b): adjust goods/install/line fees and WAIT payment amounts
        throw new BusinessException(ErrorCode.BAD_REQUEST, "改价尚未实现（P1b）");
    }

    @PostMapping("/{id}/freight")
    public ApiResponse<Void> freight(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        // TODO(P1b): set freight_amount (D11) and sync WAIT payment
        throw new BusinessException(ErrorCode.BAD_REQUEST, "运费设置尚未实现（P1b）");
    }

    @PostMapping("/{id}/payments")
    public ApiResponse<Void> createPayments(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        // TODO(P1b): split FULL into DEPOSIT+BALANCE or append WAIT stages (D10)
        throw new BusinessException(ErrorCode.BAD_REQUEST, "应付阶段拆分尚未实现（P1b）");
    }

    @PostMapping("/{id}/shipments")
    public ApiResponse<Void> createShipment(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        // TODO(P1b): create shipment per order_item / delivery method
        throw new BusinessException(ErrorCode.BAD_REQUEST, "发货录入尚未实现（P1b）");
    }

    @PostMapping("/{id}/assign")
    public ApiResponse<Void> assign(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        // TODO(P1b): assign technician / install slot on ON_SITE_INSTALL lines
        throw new BusinessException(ErrorCode.BAD_REQUEST, "派工尚未实现（P1b）");
    }
}
