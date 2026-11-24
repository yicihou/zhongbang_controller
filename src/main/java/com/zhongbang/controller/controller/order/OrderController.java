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
 * 小程序订单。要客户登录后的 JWT。支持一次提交购物车里的多行。
 * 管理端的订单接口在 {@code AdminOrderController}，不在这里。
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ApiResponse<OrderVo.Created> create(@RequestBody OrderRequests.Create request) {
        return ApiResponse.ok(orderService.create(request));
    }

    @GetMapping
    public ApiResponse<PageResult<OrderVo.Mine>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(orderService.myOrders(status, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderVo.Mine> detail(@PathVariable Long id) {
        return ApiResponse.ok(orderService.myOrder(id));
    }

    /** 还没发货、也没收到钱时，客户可以取消。已发货会得到业务码 3003。 */
    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderVo.Mine> cancel(@PathVariable Long id, @RequestBody(required = false) OrderRequests.Cancel request) {
        return ApiResponse.ok(orderService.cancelMine(id, request));
    }
}
