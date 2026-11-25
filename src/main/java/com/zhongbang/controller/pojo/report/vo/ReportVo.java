package com.zhongbang.controller.pojo.report.vo;

import java.util.List;
import java.util.Map;

/**
 * 销售毛利报表。只给老板。
 * 商品成本只加「已知成本」的行。未知成本的销售额单独放在 unknownSalesAmount，不参与毛利，也不能当成成本 0。
 * 毛利 = 已知成本那些行的不含税销售额 − 商品成本。安装费和维修收入另列，不算进毛利，也不算净利润。
 */
public final class ReportVo {

    private ReportVo() {
    }

    public record Report(String periodType, String periodKey, String scope, Totals current, Totals previous,
                         Totals yearAgo, String comparePeriodType, Map<String, Number> differences) {
    }

    public record History(List<TrendPoint> months, List<TrendPoint> days, TrendValues monthlyAverage) {
    }

    public record TrendPoint(String date, TrendValues actual, TrendValues average) {
    }

    public record TrendValues(Long salesAmount, Long receivedAmount, Long unpaidAmount, Long grossProfit) {
    }

    public record Totals(
            String periodKey,
            long salesAmount,
            long salesTaxIncluded,
            long salesTaxExcluded,
            long receivedAmount,
            long receivedTaxIncluded,
            long receivedTaxExcluded,
            long unpaidAmount,
            Long goodsCost,
            Long grossProfit,
            String grossMargin,
            long unknownSalesAmount,
            int unknownLineCount,
            long installIncome,
            long installTaxIncluded,
            long installTaxExcluded,
            long repairIncome,
            long repairTaxIncluded,
            long repairTaxExcluded,
            String from,
            String through,
            boolean available
    ) {
        public Totals withAvailability() {
            return new Totals(periodKey, salesAmount, salesTaxIncluded, salesTaxExcluded, receivedAmount,
                    receivedTaxIncluded, receivedTaxExcluded, unpaidAmount, goodsCost, grossProfit, grossMargin,
                    unknownSalesAmount, unknownLineCount, installIncome, installTaxIncluded, installTaxExcluded,
                    repairIncome, repairTaxIncluded, repairTaxExcluded, from, through, true);
        }
    }

    public record Products(String periodType, String periodKey, String scope, int periodDays, List<SkuRow> list) {
    }

    public record SkuRow(
            Long skuId,
            Long productId,
            String productName,
            String model,
            String skuName,
            int quantity,
            long salesAmount,
            long salesTaxExcluded,
            Long costAmount,
            Long grossProfit,
            String grossMargin,
            int unknownLineCount,
            int stock,
            Integer turnoverDays,
            boolean hot,
            boolean slow,
            int suggestQty
    ) {
    }

    public record Insight(boolean configured, boolean saved, String message, Analysis analysis) {
    }

    public record Analysis(
            Long id,
            String periodType,
            String periodKey,
            String scope,
            String numbersJson,
            String resultText,
            String operatorName,
            String createdAt
    ) {
    }
}
