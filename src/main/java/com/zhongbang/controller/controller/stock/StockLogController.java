package com.zhongbang.controller.controller.stock;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.pojo.stock.vo.StockVo;
import com.zhongbang.controller.service.stock.StockLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 库存流水和盘点调整。员工都能看流水，盘点只有老板能改账。
 *
 * <p>流水的口径：所有会动 {@code product_sku.stock} 的动作都要留一条，
 * 采购入库、入库单作废、销售出库、订单取消回滚、商品期初、盘点调整。
 * 少了任何一类，老板按流水对账时就对不上余额了。
 */
@RestController
public class StockLogController {

    private final StockLogService stockLogService;

    public StockLogController(StockLogService stockLogService) {
        this.stockLogService = stockLogService;
    }

    @GetMapping("/api/admin/stock-logs")
    public ApiResponse<PageResult<StockVo.LogRow>> logs(
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(stockLogService.logs(skuId, bizType, from, to, page, size));
    }

    /**
     * 盘点调整：把某个规格的账面库存改成老板数出来的实际数量，系统自己记差额。
     * 只有老板能调，业务员看到的库存是只读的。
     */
    @PostMapping("/api/admin/stock-adjust")
    public ApiResponse<StockVo.AdjustResult> adjust(@RequestBody Map<String, Object> body) {
        Object rawSku = body.get("skuId");
        if (rawSku == null || String.valueOf(rawSku).isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择规格");
        }
        Object rawActual = body.get("actualStock");
        if (rawActual == null || String.valueOf(rawActual).isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写实际库存");
        }
        long skuId = Long.parseLong(String.valueOf(rawSku));
        int actual = Integer.parseInt(String.valueOf(rawActual));
        Object reason = body.get("reason");
        return ApiResponse.ok(stockLogService.adjust(skuId, actual, reason == null ? null : String.valueOf(reason)));
    }
}
