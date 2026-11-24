package com.zhongbang.controller;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 程序入口。IDEA 里运行这个类的 {@code main}，或在仓库根目录执行 {@code mvn spring-boot:run}。
 *
 * <p>{@code @SpringBootApplication} 表示：这是启动类，并自动扫描本包及子包里的组件，
 * 同时按 classpath 上的依赖打开 Web、数据源等自动配置。
 * {@code @MapperScan} 让 MyBatis 把 {@code module} 下标了 {@code @Mapper} 的接口变成可用的数据库访问对象。
 * 不写这行的话，{@code CustomerMapper} 注入时会找不到 Bean。
 */
@SpringBootApplication
@MapperScan("com.zhongbang.controller.module")
public class ZhongbangControllerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhongbangControllerApplication.class, args);
    }
}
