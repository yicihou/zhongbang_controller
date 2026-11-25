package com.zhongbang.controller.pojo.purchase.vo;

import java.util.List;

/**
 * 供应商和采购单返回值。
 * 带金额的字段在非老板的响应里是空的，Jackson 会把空字段省掉，所以业务员看不到进价和应付。
 */
public final class PurchaseVo {

    private PurchaseVo() {
    }

    public record SupplierItem(
            Long id,
            String name,
            String contact,
            String mobile,
            String brands,
            Integer accountDays,
            String remark,
            Long payable,
            Long paid,
            Long balance
    ) {
    }

    public record LedgerRow(
            Long id,
            Long supplierId,
            Long purchaseId,
            String entryType,
            long amount,
            String method,
            String remark,
            String operatorName,
            String createdAt
    ) {
    }

    public record Ledger(
            Long supplierId,
            String supplierName,
            long payable,
            long paid,
            long balance,
            List<LedgerRow> rows
    ) {
    }

    public record Line(
            Long id,
            Long skuId,
            String productName,
            String skuName,
            int quantity,
            Long unitCost,
            Long lineAmount
    ) {
    }

    public record Detail(
            Long id,
            String purchaseNo,
            Long supplierId,
            String supplierName,
            String orderDate,
            String status,
            boolean invoiced,
            String invoiceType,
            Integer taxRate,
            Boolean taxIncluded,
            List<String> attachmentUrls,
            String remark,
            String operatorName,
            String createdAt,
            List<Line> lines,
            Long freightAmount,
            Long goodsAmount,
            Long totalAmount
    ) {
    }
}
