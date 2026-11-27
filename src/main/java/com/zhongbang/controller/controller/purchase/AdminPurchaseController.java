package com.zhongbang.controller.controller.purchase;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.pojo.purchase.dto.PurchaseRequests;
import com.zhongbang.controller.pojo.purchase.vo.PurchaseVo;
import com.zhongbang.controller.service.purchase.PurchaseService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 采购主单与供应商子单。老板可以更正或作废已入库的子单。
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

    @GetMapping("/batches")
    public ApiResponse<PageResult<PurchaseVo.BatchRow>> batches(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(purchaseService.listBatches(keyword, page, size));
    }

    @GetMapping("/batches/{id}")
    public ApiResponse<PurchaseVo.Batch> batch(@PathVariable long id) {
        return ApiResponse.ok(purchaseService.getBatch(id));
    }

    @PostMapping("/batches")
    public ApiResponse<PurchaseVo.Batch> createBatch(@RequestBody PurchaseRequests.BatchCreate request) {
        try {
            return ApiResponse.ok(purchaseService.createBatch(request));
        } catch (DuplicateKeyException ex) {
            PurchaseVo.Batch existing = purchaseService.retryBatch(request);
            if (existing == null) {
                throw ex;
            }
            return ApiResponse.ok(existing);
        }
    }

    @PostMapping("/{id}/correction")
    public ApiResponse<PurchaseVo.Detail> correct(@PathVariable long id, @RequestBody PurchaseRequests.Correction request) {
        return ApiResponse.ok(purchaseService.correct(id, request));
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
    public ApiResponse<PurchaseVo.Detail> receive(@PathVariable long id, @RequestParam(required = false) Integer revision) {
        return ApiResponse.ok(purchaseService.receive(id, revision));
    }

    @PostMapping("/{id}/void")
    public ApiResponse<PurchaseVo.Detail> voidPurchase(@PathVariable long id, @RequestParam(required = false) Integer revision) {
        return ApiResponse.ok(purchaseService.voidPurchase(id, revision));
    }
}
