package com.zhongbang.controller.controller.purchase;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.pojo.purchase.dto.PurchaseRequests;
import com.zhongbang.controller.pojo.purchase.vo.PurchaseVo;
import com.zhongbang.controller.service.purchase.PurchaseService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 供应商。基本资料管理员可以维护。
 * 应付、已付和余额只在老板的返回里出现，登记付款也只有老板能做。
 */
@RestController
@RequestMapping("/api/admin/suppliers")
public class AdminSupplierController {

    private final PurchaseService purchaseService;

    public AdminSupplierController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @GetMapping
    public ApiResponse<PageResult<PurchaseVo.SupplierItem>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(purchaseService.listSuppliers(keyword, page, size));
    }

    @PostMapping
    public ApiResponse<PurchaseVo.SupplierItem> create(@RequestBody PurchaseRequests.SupplierSave request) {
        return ApiResponse.ok(purchaseService.saveSupplier(null, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<PurchaseVo.SupplierItem> update(@PathVariable long id, @RequestBody PurchaseRequests.SupplierSave request) {
        return ApiResponse.ok(purchaseService.saveSupplier(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        purchaseService.deleteSupplier(id);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/ledger")
    public ApiResponse<PurchaseVo.Ledger> ledger(@PathVariable long id) {
        return ApiResponse.ok(purchaseService.ledger(id));
    }

    @PostMapping("/{id}/payments")
    public ApiResponse<PurchaseVo.Ledger> pay(@PathVariable long id, @RequestBody PurchaseRequests.Payment request) {
        return ApiResponse.ok(purchaseService.pay(id, request));
    }
}
