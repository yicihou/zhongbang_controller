package com.zhongbang.controller.controller.purchase;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.pojo.purchase.dto.PurchaseRequests;
import com.zhongbang.controller.pojo.purchase.vo.PurchaseVo;
import com.zhongbang.controller.service.purchase.PurchaseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 采购单。管理员可以录入、入库和作废，但看不到进价、运费和合计。
 * 确认入库会加库存并写流水。作废已入库的单子会把库存减回去。
 * 进货单照片先走 POST /api/admin/files，再把返回的地址放进 attachmentUrls。
 */
@RestController
@RequestMapping("/api/admin/purchases")
public class AdminPurchaseController {

    private final PurchaseService purchaseService;

    public AdminPurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @GetMapping
    public ApiResponse<PageResult<PurchaseVo.Detail>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(purchaseService.listPurchases(status, keyword, page, size));
    }

    @PostMapping
    public ApiResponse<PurchaseVo.Detail> create(@RequestBody PurchaseRequests.PurchaseSave request) {
        return ApiResponse.ok(purchaseService.savePurchase(null, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<PurchaseVo.Detail> detail(@PathVariable long id) {
        return ApiResponse.ok(purchaseService.getPurchase(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<PurchaseVo.Detail> update(@PathVariable long id, @RequestBody PurchaseRequests.PurchaseSave request) {
        return ApiResponse.ok(purchaseService.savePurchase(id, request));
    }

    @PostMapping("/{id}/receive")
    public ApiResponse<PurchaseVo.Detail> receive(@PathVariable long id) {
        return ApiResponse.ok(purchaseService.receive(id));
    }

    @PostMapping("/{id}/void")
    public ApiResponse<PurchaseVo.Detail> voidPurchase(@PathVariable long id) {
        return ApiResponse.ok(purchaseService.voidPurchase(id));
    }
}
