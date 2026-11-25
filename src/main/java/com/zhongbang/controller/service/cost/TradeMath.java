package com.zhongbang.controller.service.cost;

/**
 * 进价和税额的整数算法。金额单位是分，税率是「百分数乘 100」（1300 表示 13.00%）。
 * 全部用 long 相乘再除，避免小数二进制误差。除法只在最后做一次，余数舍去。
 */
public final class TradeMath {

    private TradeMath() {
    }

    /** 含税金额和不含税金额。不开票，或开了票但没填税率时，两边都等于原金额（税额为 0）。 */
    public record TaxParts(long included, long excluded) {
    }

    /**
     * @param invoiced    是否开票
     * @param taxIncluded 1 原金额已经含税，0 原金额不含税。开票但没填时按含税理解
     * @param rateBp      税率，1300 表示 13%。空或 0 表示不算税
     */
    public static TaxParts split(long amount, boolean invoiced, Integer taxIncluded, Integer rateBp) {
        if (!invoiced || rateBp == null || rateBp <= 0) {
            return new TaxParts(amount, amount);
        }
        if (taxIncluded != null && taxIncluded == 0) {
            long included = amount * (10_000L + rateBp) / 10_000L;
            return new TaxParts(included, amount);
        }
        long excluded = amount * 10_000L / (10_000L + rateBp);
        return new TaxParts(amount, excluded);
    }

    /**
     * 移动加权平均。旧进价未知时，不用 0 去乘旧库存，直接采用本批进价。
     * 旧库存为 0 时也采用本批进价。
     */
    public static long average(Long oldCost, int oldStock, long unitCost, int qty) {
        if (qty <= 0) {
            return unitCost;
        }
        if (oldCost == null || oldStock <= 0) {
            return unitCost;
        }
        return (oldCost * (long) oldStock + unitCost * qty) / (oldStock + qty);
    }

    /**
     * 作废入库时，从当前库存金额里扣掉这一批。扣完如果金额为负，退回入库前的进价。
     * 库存扣到 0 时也退回入库前的进价（可能是空，表示当时还不知道成本）。
     */
    public static Long rollbackAverage(Long currentCost, int stockBeforeVoid, long unitCost, int qty, Long costBefore) {
        int next = stockBeforeVoid - qty;
        if (next <= 0 || currentCost == null) {
            return costBefore;
        }
        long value = currentCost * (long) stockBeforeVoid - unitCost * qty;
        if (value < 0) {
            return costBefore;
        }
        return value / next;
    }

    /** 建议采购量 = 最低库存 × 系数 − 现有库存 − 在途。小于 0 时记 0。 */
    public static int suggest(int minStock, int multiplier, int stock, int inTransit) {
        int factor = multiplier <= 0 ? 2 : multiplier;
        int raw = minStock * factor - Math.max(0, stock) - Math.max(0, inTransit);
        return Math.max(0, raw);
    }

    /**
     * 毛利率，百分数保留两位，例如 32.50。销售额为 0 时返回空，而不是 0。
     * 利润为负时带减号。
     */
    public static String marginText(long profit, long netSales) {
        if (netSales <= 0) {
            return null;
        }
        boolean negative = profit < 0;
        long abs = Math.abs(profit);
        long basis = (abs * 10_000L + netSales / 2) / netSales;
        String text = (basis / 100) + "." + String.format("%02d", basis % 100);
        return negative ? "-" + text : text;
    }

    /** 周转天数 = 现有库存 / 日均销量。销量为 0 时算不出来，返回空。 */
    public static Integer turnoverDays(int stock, int soldQty, long periodDays) {
        if (soldQty <= 0) {
            return null;
        }
        long days = periodDays <= 0 ? 1 : periodDays;
        double daily = soldQty / (double) days;
        return (int) Math.round(stock / daily);
    }
}
