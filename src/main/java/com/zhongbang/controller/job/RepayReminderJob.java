package com.zhongbang.controller.job;

import com.zhongbang.controller.service.customer.RepayAgreementService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 每天早上检查协议还款。默认 9:00，时区是上海，表达式可用环境变量 CUSTOMER_REMIND_CRON 改。
 * 到期前 3 天、当天、已逾期各写一条只给老板的通知。同一天不会重复写。
 */
@Component
public class RepayReminderJob {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final RepayAgreementService agreementService;

    public RepayReminderJob(RepayAgreementService agreementService) {
        this.agreementService = agreementService;
    }

    @Scheduled(cron = "${zhongbang.customer.remind-cron:0 0 9 * * *}", zone = "Asia/Shanghai")
    public void remind() {
        agreementService.scan(LocalDate.now(SHANGHAI));
    }
}
