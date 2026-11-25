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

    /**
     * 一条库存流水。bizType 见 StockLogService 上的常量：
     * INIT、PURCHASE_IN、PURCHASE_VOID、SALE_OUT、SALE_CANCEL、ADJUST。
     * quantity 正数是增加，负数是减少；stockAfter 是这一笔之后的余额。
     */
    public record LogRow(
            Long id,
            Long skuId,
            String productName,
            String model,
            String skuName,
            String bizType,
            Long bizId,
            int quantity,
            int stockAfter,
            Long unitCost,
            String operatorName,
            String createdAt
    ) {
    }

    /** 盘点调整的结果。beforeStock 和 actualStock 的差就是这一笔流水的数量。 */
    public record AdjustResult(
            Long skuId,
            int beforeStock,
            int actualStock,
            int delta,
            String reason
    ) {
    }
}
