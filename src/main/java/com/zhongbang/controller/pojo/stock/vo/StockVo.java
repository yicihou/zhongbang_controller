package com.zhongbang.controller.pojo.stock.vo;

import java.util.List;

/** 库存预警列表。这里没有进价。建议采购量按「最低库存 × 系数 − 现有 − 在途」计算。 */
public final class StockVo {

    private StockVo() {
    }

    public record Page(
            int defaultMinStock,
            int suggestMultiplier,
            List<Row> list
    ) {
    }

    public record Row(
            Long skuId,
            Long productId,
            String productName,
            String model,
            String skuName,
            int stock,
            int minStock,
            boolean usingDefaultMin,
            int inTransit,
            int suggestQty,
            boolean alerting
    ) {
    }
}
