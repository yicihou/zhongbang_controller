package com.zhongbang.controller;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * 程序入口。IDEA 里运行这个类的 {@code main}，或在仓库根目录执行 {@code mvn spring-boot:run}。
 *
 * <p>{@code @SpringBootApplication} 表示：这是启动类，并自动扫描本包及子包里的组件，
 * 同时按 classpath 上的依赖打开 Web、数据源等自动配置。
 * {@code exclude = UserDetailsServiceAutoConfiguration} 是因为员工密码用自己的 BCrypt 校验，
 * 不需要 Spring Security 再生成一个内存里的默认账号。
 * {@code @MapperScan} 扫描 {@code mapper} 包及其子包（例如 {@code mapper.customer}），
 * 把标了 {@code @Mapper} 的接口变成可用的数据库访问对象。
 * 不写这行的话，{@code CustomerMapper} 注入时会找不到 Bean。
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@MapperScan("com.zhongbang.controller.mapper")
public class ZhongbangControllerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhongbangControllerApplication.class, args);
    }
}
