package com.zhongbang.controller;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.zhongbang.controller.module")
public class ZhongbangControllerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhongbangControllerApplication.class, args);
    }
}
