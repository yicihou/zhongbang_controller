package com.zhongbang.controller.service.customer;

import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 客户模块里不碰数据库的小规则：类型和来源怎么写、手机号和地址怎么打码、地区怎么从地址里摘出来。
 * 打码只用于发给分析模型的摘要。老板自己的页面仍显示完整手机号和地址。
 */
public final class CustomerRules {

    public static final Map<String, String> TYPES = labels(
            "PERSONAL", "个人",
            "FACTORY", "服装厂",
            "WORKSHOP", "加工作坊",
            "SCHOOL", "学校/培训班",
            "DEALER", "同行经销商"
    );

    public static final Map<String, String> SOURCES = labels(
            "PHONE", "电话下单",
            "STORE", "门店散客",
            "MINIAPP", "小程序",
            "REFERRAL", "转介绍",
            "OTHER", "其他",
            "DEMO", "演示数据"
    );

    private CustomerRules() {
    }

    public static String typeLabel(String code) {
        if (code == null || code.isBlank()) {
            return "个人";
        }
        return TYPES.getOrDefault(code.trim().toUpperCase(Locale.ROOT), code);
    }

    public static String sourceLabel(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        return SOURCES.getOrDefault(code.trim().toUpperCase(Locale.ROOT), code);
    }

    /** 接受编码或中文。空则用默认值。对不上就报参数错误。 */
    public static String normalizeType(String raw, String fallback) {
        return normalize(raw, TYPES, fallback, "客户类型");
    }

    public static String normalizeSource(String raw, String fallback) {
        return normalize(raw, SOURCES, fallback, "来源");
    }

    /** 13812348000 变成 138****8000。太短的原样返回，避免打码后更难看。 */
    public static String maskMobile(String mobile) {
        if (mobile == null || mobile.isBlank()) {
            return "";
        }
        String text = mobile.trim();
        if (text.length() < 7) {
            return text;
        }
        return text.substring(0, 3) + "****" + text.substring(text.length() - 4);
    }

    /**
     * 从地址里取出「市 + 区/县」，去掉省字和门牌。
     * 例如「浙江省杭州市西湖区文三路 188 号」得到「杭州市西湖区」。
     */
    public static String guessRegion(String address) {
        if (address == null) {
            return "";
        }
        String text = address.trim();
        if (text.isEmpty()) {
            return "";
        }
        int city = text.indexOf('市');
        if (city < 0) {
            return text.length() > 32 ? text.substring(0, 32) : text;
        }
        int start = 0;
        int province = text.lastIndexOf('省', city);
        if (province >= 0) {
            start = province + 1;
        }
        int end = -1;
        for (int i = city + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '区' || c == '县' || c == '旗') {
                end = i;
                break;
            }
        }
        if (end > city) {
            return text.substring(start, end + 1);
        }
        return text.substring(start, city + 1);
    }

    /** 分析摘要里用的地址：只留到区，后面用星号，不带门牌。 */
    public static String maskAddress(String address) {
        String region = guessRegion(address);
        if (region.isBlank()) {
            return "";
        }
        return region + "***";
    }

    public static long unpaid(Long payable, Long paid) {
        long left = (payable == null ? 0L : payable) - (paid == null ? 0L : paid);
        return Math.max(left, 0L);
    }

    public static boolean cancelled(String status) {
        return "CANCELLED".equals(status);
    }

    /** 按单价（分）划一个给人看的价格区间。 */
    public static String priceBand(long unitPriceFen) {
        if (unitPriceFen < 200_000L) {
            return "2000元以下";
        }
        if (unitPriceFen <= 800_000L) {
            return "2000-8000元";
        }
        return "8000元以上";
    }

    public static String payMethodLabel(String offlineMethod, String channel) {
        String method = offlineMethod == null ? "" : offlineMethod.trim().toUpperCase(Locale.ROOT);
        return switch (method) {
            case "BANK_TRANSFER" -> "银行转账";
            case "WECHAT_TRANSFER" -> "微信转账";
            case "ALIPAY" -> "支付宝";
            case "CASH" -> "现金";
            case "OTHER" -> "其他";
            default -> "WXPAY".equalsIgnoreCase(channel) ? "微信支付" : (channel == null || channel.isBlank() ? "未知" : "线下");
        };
    }

    public static String tags(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String joined = String.join(",", java.util.Arrays.stream(raw.split("[,，]"))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList());
        if (joined.length() > 255) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标签不能超过 255 个字");
        }
        return joined.isEmpty() ? null : joined;
    }

    private static String normalize(String raw, Map<String, String> dict, String fallback, String label) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String text = raw.trim();
        String upper = text.toUpperCase(Locale.ROOT);
        if (dict.containsKey(upper)) {
            return upper;
        }
        for (Map.Entry<String, String> entry : dict.entrySet()) {
            if (entry.getValue().equals(text)) {
                return entry.getKey();
            }
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, label + "不正确");
    }

    private static Map<String, String> labels(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return Map.copyOf(map);
    }
}
