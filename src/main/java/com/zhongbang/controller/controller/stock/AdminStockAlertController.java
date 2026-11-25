package com.zhongbang.controller.controller.stock;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.pojo.stock.vo.StockVo;
import com.zhongbang.controller.service.stock.StockAlertService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 库存预警。员工都能看。低于最低库存时另外写通知推给老板，这里只是列表和建议采购量。
 * 建议采购量 = 最低库存 × 系数 − 现有库存 − 草稿采购单里的在途数量。
 */
@RestController
@RequestMapping("/api/admin/stock-alerts")
public class AdminStockAlertController {

    private final StockAlertService stockAlertService;

    public AdminStockAlertController(StockAlertService stockAlertService) {
        this.stockAlertService = stockAlertService;
    }

    @GetMapping
    public ApiResponse<StockVo.Page> list() {
        return ApiResponse.ok(stockAlertService.list());
    }

    @PutMapping("/skus/{skuId}")
    public ApiResponse<StockVo.Page> minStock(@PathVariable long skuId, @RequestBody Map<String, Object> body) {
        Object raw = body.get("minStock");
        Integer min = raw == null || String.valueOf(raw).isBlank() ? null : Integer.valueOf(String.valueOf(raw));
        stockAlertService.updateMinStock(skuId, min);
        return ApiResponse.ok(stockAlertService.list());
    }
}
