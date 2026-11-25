package com.zhongbang.controller.service.customer;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 只有 dev，并且 zhongbang.demo.auto-seed=true 时，启动后自动生成演示客户。
 * 默认不打开，避免测试一跑就把 30 个客户写进内存库。生产 profile 不会装这个类。
 */
@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "zhongbang.demo", name = "auto-seed", havingValue = "true")
public class CustomerDemoInitializer implements ApplicationRunner {

    private final CustomerDemoService demoService;

    public CustomerDemoInitializer(CustomerDemoService demoService) {
        this.demoService = demoService;
    }

    @Override
    public void run(ApplicationArguments args) {
        demoService.seedInternal();
    }
}
