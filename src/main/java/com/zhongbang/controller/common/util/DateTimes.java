package com.zhongbang.controller.common.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 接口里的时间统一写成 {@code yyyy-MM-dd HH:mm:ss}，按上海时区取「现在」。
 * 不把时间戳数字返回给页面。管理端比较收款时间时也按这个格式。
 */
public final class DateTimes {

    public static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private DateTimes() {
    }

    /** 当前上海时间，不带时区后缀。 */
    public static LocalDateTime now() {
        return LocalDateTime.now(SHANGHAI);
    }

    public static String format(LocalDateTime time) {
        return time == null ? null : time.format(FMT);
    }

    public static String nowText() {
        return format(now());
    }
}
