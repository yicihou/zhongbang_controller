package com.zhongbang.controller.pojo.customer.dto;

import com.zhongbang.controller.pojo.customer.vo.CustomerVo;

import java.util.List;

/**
 * 页面提交客户资料时用的 JSON。字段都可以缺，缺的由服务层决定是报错还是用默认值。
 */
public final class CustomerRequests {

    private CustomerRequests() {
    }

    public record Profile(
            String name,
            String mobile,
            String altMobile,
            String wechat,
            String customerType,
            String shopName,
            Integer machineCount,
            String mainProducts,
            String source,
            String referrer,
            String birthday,
            String remark,
            String tags,
            String region
    ) {
    }

    public record Address(
            String region,
            String detail,
            String contactName,
            String contactMobile,
            Boolean defaultAddress
    ) {
    }

    public record Follow(String followDate, String content, String nextDate) {
    }

    public record Preference(
            String manualNote,
            String manualBrand,
            String manualCategory,
            String manualPriceBand,
            String manualPayMethod,
            Boolean manualNeedInstall
    ) {
    }

    /** manual=true 时锁定等级；false 时取消锁定并按累计实收重算。 */
    public record LevelAssign(String levelCode, Boolean manual) {
    }

    public record LevelRule(String name, Long minPaid, Boolean enabled) {
    }

    public record Agreement(
            String scope,
            Long orderId,
            Long paymentId,
            String dueDate,
            Long agreedAmount,
            String note,
            List<CustomerVo.Material> materials
    ) {
    }
}
