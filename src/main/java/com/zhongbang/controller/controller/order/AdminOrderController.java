package com.zhongbang.controller.controller.order;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.pojo.order.dto.OrderRequests;
import com.zhongbang.controller.pojo.order.vo.OrderVo;
import com.zhongbang.controller.service.order.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端订单。要 STAFF 令牌。发货和派师傅不要求订单已经付清。
 * 已发货的订单不能在这里直接取消。
 */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ApiResponse<PageResult<OrderVo.Admin>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(orderService.adminList(status, keyword, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderVo.Admin> detail(@PathVariable Long id) {
        return ApiResponse.ok(orderService.adminGet(id));
    }

    @PostMapping("/{id}/adjust-price")
    public ApiResponse<OrderVo.Admin> adjust(@PathVariable Long id, @RequestBody OrderRequests.AdjustPrice request) {
        return ApiResponse.ok(orderService.adjust(id, request));
    }

    @PostMapping("/{id}/freight")
    public ApiResponse<OrderVo.Admin> freight(@PathVariable Long id, @RequestBody OrderRequests.Freight request) {
        return ApiResponse.ok(orderService.freight(id, request));
    }

    @PostMapping("/{id}/payments")
    public ApiResponse<OrderVo.Admin> payments(@PathVariable Long id, @RequestBody OrderRequests.CreatePayments request) {
        return ApiResponse.ok(orderService.createPayments(id, request));
    }

    /** 填写物流单号。未付款也可以发。 */
    @PostMapping("/{id}/shipments")
    public ApiResponse<OrderVo.Admin> ship(@PathVariable Long id, @RequestBody OrderRequests.ShipmentCreate request) {
        return ApiResponse.ok(orderService.ship(id, request));
    }

    @PostMapping("/{id}/assign")
    public ApiResponse<OrderVo.Admin> assign(@PathVariable Long id, @RequestBody OrderRequests.Assign request) {
        return ApiResponse.ok(orderService.assign(id, request));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderVo.Admin> cancel(@PathVariable Long id, @RequestBody OrderRequests.Cancel request) {
        return ApiResponse.ok(orderService.cancel(id, request));
    }
}
