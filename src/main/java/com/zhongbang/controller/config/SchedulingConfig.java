package com.zhongbang.controller.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 打开 Spring 的定时任务。协议还款提醒在 job 包里。 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
