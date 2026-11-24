package com.zhongbang.controller.config;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 主键生成器。MyBatis-Plus 默认的雪花 id 有 19 位，超过浏览器里 number 能精确表示的范围，
 * 管理端把订单 id 放进地址栏时会把末尾数字改掉，接着查详情就 404。
 *
 * <p>这里从当前毫秒乘 1000 开始往上加，结果小于 2 的 53 次方，JavaScript 不会丢精度。
 * 种子数据里的 1、101 这类小 id 和它不会撞车。只在本进程内递增，dev 的内存库每次重启都是空的。
 */
@Component
public class JsSafeIdGenerator implements IdentifierGenerator {

    private final AtomicLong sequence = new AtomicLong(System.currentTimeMillis() * 1000L);

    @Override
    public Number nextId(Object entity) {
        return sequence.incrementAndGet();
    }
}
