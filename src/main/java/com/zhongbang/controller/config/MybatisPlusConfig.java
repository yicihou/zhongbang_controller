package com.zhongbang.controller.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 的分页插件。没有它时，分页查询不会自动改写成带 LIMIT 的 SQL。
 * 方言固定为 MySQL。本地 dev 用的 H2 开了 MySQL 兼容模式，所以同一套分页语句两边都能跑。
 * 目前业务查询都是手写条件，还没有走这个分页拦截器的接口。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
