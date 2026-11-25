package com.zhongbang.controller.pojo.customer.vo;

import java.util.List;

/**
 * 客户模块返回给页面的 JSON 形状。金额单位都是分，和订单接口一样。
 * 嵌在一个类里，是为了这些小结构不用各占一个文件。页面按字段名读取。
 */
public final class CustomerVo {

    private CustomerVo() {
    }

    /** 列表一行。待付款和订单总额分两列，overdue 表示有已过期还没履约的协议。 */
    public record ListItem(
            Long id,
            String name,
            String mobile,
            String region,
            String customerType,
            String customerTypeLabel,
            Integer level,
            String levelCode,
            String levelName,
            boolean levelManual,
            String tags,
            long orderAmount,
            long unpaidAmount,
            int orderCount,
            String lastOrderAt,
            boolean overdue
    ) {
    }

    public record Profile(
            Long id,
            String name,
            String mobile,
            String altMobile,
            String wechat,
            String customerType,
            String customerTypeLabel,
            String shopName,
            Integer machineCount,
            String mainProducts,
            String source,
            String sourceLabel,
            String referrer,
            String birthday,
            String remark,
            String tags,
            String region,
            Integer level,
            String levelCode,
            String levelName,
            boolean levelManual,
            String createdAt
    ) {
    }

    /** 消费统计。avgAmount 没有订单时为空。daysSinceLast 是距今天数，没有订单时为空。 */
    public record Stats(
            long orderAmount,
            long receivedAmount,
            long unpaidAmount,
            int orderCount,
            Long avgAmount,
            String firstOrderAt,
            String lastOrderAt,
            Long daysSinceLast
    ) {
    }

    public record Address(
            Long id,
            String region,
            String detail,
            String contactName,
            String contactMobile,
            boolean defaultAddress,
            String source
    ) {
    }

    public record OrderBrief(
            Long id,
            String orderNo,
            String status,
            long payableAmount,
            long paidAmount,
            long unpaidAmount,
            String createdAt
    ) {
    }

    public record ModelStat(
            String model,
            String brand,
            String category,
            int times,
            int quantity,
            long amount,
            String lastAt
    ) {
    }

    public record AssetBrief(
            Long id,
            String brand,
            String model,
            String sn,
            String source,
            String buyDate,
            String warrantyEnd,
            boolean warrantyExpired,
            int repairCount
    ) {
    }

    public record RepairBrief(
            Long id,
            String ticketNo,
            String status,
            long payableAmount,
            long paidAmount,
            long unpaidAmount,
            String createdAt
    ) {
    }

    /**
     * 购买偏好。auto 开头的是按订单算出来的，manual 开头的是老板补的。
     * manual 某一项为空，页面就只显示自动结果。
     */
    public record Preference(
            String autoBrand,
            String autoCategory,
            String autoPriceBand,
            String autoPayMethod,
            Boolean autoNeedInstall,
            String manualNote,
            String manualBrand,
            String manualCategory,
            String manualPriceBand,
            String manualPayMethod,
            Boolean manualNeedInstall
    ) {
    }

    public record Follow(
            Long id,
            String followDate,
            String content,
            String nextDate,
            String operatorName,
            String createdAt
    ) {
    }

    /** 协议上的一份材料。TEXT 用 text，其他用 url 播放或下载。 */
    public record Material(String kind, String url, String name, String contentType, String text) {
    }

    public record Agreement(
            Long id,
            Long customerId,
            String customerName,
            String scope,
            Long orderId,
            String orderNo,
            Long paymentId,
            String paymentTitle,
            String dueDate,
            long agreedAmount,
            String status,
            String note,
            List<Material> materials,
            String operatorName,
            String createdAt,
            String updatedAt,
            String bucket
    ) {
    }

    public record AgreementLog(Long id, String action, String snapshot, String operatorName, String createdAt) {
    }

    public record LevelRule(Long id, String code, String name, long minPaid, int rankNo, boolean enabled) {
    }

    public record Analysis(Long id, Long customerId, String kind, String promptText, String resultText, String operatorName, String createdAt) {
    }

    /**
     * 分析接口始终用 HTTP 200 返回这个结构。
     * available=false 表示没配密钥；ok=false 表示调用失败。两种都不会变成 500。
     */
    public record Insight(boolean available, boolean ok, String message, Analysis analysis) {
    }

    public record NameCount(String name, int count) {
    }

    public record NameAmount(String name, Long id, long quantity, long amount) {
    }

    /** 不调用模型也能画的统计。 */
    public record Overview(
            List<NameCount> levels,
            List<NameCount> regions,
            List<NameAmount> models,
            List<NameAmount> unpaidTop,
            int customerCount,
            int unpaidCustomerCount,
            int overdueCount,
            int sleepingCount,
            long unpaidAmount
    ) {
    }

    public record Detail(
            Profile profile,
            Stats stats,
            List<Address> addresses,
            List<OrderBrief> orders,
            List<ModelStat> models,
            List<AssetBrief> assets,
            List<RepairBrief> repairs,
            Preference preference,
            List<Follow> follows,
            List<Agreement> agreements,
            boolean overdue
    ) {
    }

    public record DemoResult(boolean created, int customers, String message) {
    }

    public record ImportResult(int created, int skipped, List<String> errors) {
    }
}
