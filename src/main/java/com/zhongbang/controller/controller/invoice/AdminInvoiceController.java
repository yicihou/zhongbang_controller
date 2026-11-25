package com.zhongbang.controller.controller.invoice;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.pojo.invoice.dto.InvoiceRequest;
import com.zhongbang.controller.service.invoice.InvoiceService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 给订单和报修单标记开票。管理员也能改，因为这不是进价。
 * 税率按百分数乘 100，13% 传 1300，可以不填。
 */
@RestController
public class AdminInvoiceController {

    private final InvoiceService invoiceService;

    public AdminInvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PutMapping("/api/admin/orders/{id}/invoice")
    public ApiResponse<Void> order(@PathVariable long id, @RequestBody InvoiceRequest request) {
        invoiceService.markOrder(id, request);
        return ApiResponse.ok();
    }

    @PutMapping("/api/admin/repairs/{id}/invoice")
    public ApiResponse<Void> repair(@PathVariable long id, @RequestBody InvoiceRequest request) {
        invoiceService.markRepair(id, request);
        return ApiResponse.ok();
    }
}
