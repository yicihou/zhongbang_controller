package com.zhongbang.controller.service.customer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhongbang.controller.adapter.deepseek.DeepSeekClient;
import com.zhongbang.controller.adapter.deepseek.DeepSeekException;
import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.config.ZhongbangProperties;
import com.zhongbang.controller.mapper.customer.CustomerAnalysisMapper;
import com.zhongbang.controller.pojo.customer.entity.CustomerAnalysis;
import com.zhongbang.controller.pojo.customer.vo.CustomerVo;
import com.zhongbang.controller.security.StaffRoles;
import com.zhongbang.controller.security.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把客户资料整理成短摘要，交给 DeepSeek 写中文建议。
 * 摘要里的手机号打码、地址只留到区。没配密钥或调用失败时返回说明，不抛 500。
 */
@Service
public class CustomerInsightService {

    private static final Logger log = LoggerFactory.getLogger(CustomerInsightService.class);
    private static final String MISSING_KEY = "还没有配置 DeepSeek。请在启动环境里设置 DEEPSEEK_API_KEY 后再试。";
    private static final String ONE_SYSTEM = """
            你是缝纫机经销店的客户分析助手。只根据给定资料用中文回答，不要编造资料里没有的数字。
            请分五段：客户画像、购买预测或复购时机、推荐机型或耗材、风险提示（例如欠款或逾期）、跟进建议。
            每段不超过 120 字。资料中的手机号和地址已脱敏，不要尝试还原。
            """;
    private static final String ALL_SYSTEM = """
            你是缝纫机经销店的经营分析助手。只根据给定的汇总数字用中文给出经营建议。
            请分四段：客户结构、热销与地区、欠款与逾期风险、下一步动作。每段不超过 150 字。不要编造未提供的数字。
            """;

    private final CustomerQueryService queryService;
    private final CustomerAnalysisMapper analysisMapper;
    private final DeepSeekClient deepSeekClient;
    private final ZhongbangProperties properties;
    private final ObjectMapper objectMapper;

    public CustomerInsightService(
            CustomerQueryService queryService,
            CustomerAnalysisMapper analysisMapper,
            DeepSeekClient deepSeekClient,
            ZhongbangProperties properties,
            ObjectMapper objectMapper
    ) {
        this.queryService = queryService;
        this.analysisMapper = analysisMapper;
        this.deepSeekClient = deepSeekClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public CustomerVo.Insight analyzeCustomer(Long customerId) {
        StaffRoles.requireBoss();
        if (!properties.getDeepseek().configured()) {
            return missing();
        }
        CustomerVo.Detail detail = queryService.detail(customerId);
        return call("CUSTOMER", customerId, customerPrompt(detail), ONE_SYSTEM);
    }

    public CustomerVo.Insight analyzeOverall() {
        StaffRoles.requireBoss();
        if (!properties.getDeepseek().configured()) {
            return missing();
        }
        return call("OVERALL", null, overallPrompt(queryService.overview()), ALL_SYSTEM);
    }

    public List<CustomerVo.Analysis> history(Long customerId, String kind) {
        StaffRoles.requireBoss();
        LambdaQueryWrapper<CustomerAnalysis> wrapper = new LambdaQueryWrapper<CustomerAnalysis>()
                .orderByDesc(CustomerAnalysis::getCreatedAt);
        if (customerId != null) {
            wrapper.eq(CustomerAnalysis::getCustomerId, customerId).eq(CustomerAnalysis::getKind, "CUSTOMER");
        } else {
            wrapper.eq(CustomerAnalysis::getKind, kind == null || kind.isBlank() ? "OVERALL" : kind.trim().toUpperCase());
        }
        return analysisMapper.selectList(wrapper.last("limit 20")).stream().map(this::toVo).toList();
    }

    /** 单个客户发给模型的摘要。测试会检查这里没有完整手机号和门牌。 */
    public String customerPrompt(CustomerVo.Detail detail) {
        CustomerVo.Profile profile = detail.profile();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("姓名", profile.name());
        body.put("客户类型", profile.customerTypeLabel());
        body.put("地区", profile.region());
        body.put("等级", profile.levelName());
        body.put("标签", profile.tags());
        body.put("厂名或店名", profile.shopName());
        body.put("厂里机器数量", profile.machineCount());
        body.put("主营产品", profile.mainProducts());
        body.put("来源", profile.sourceLabel());
        body.put("介绍人", profile.referrer());
        body.put("手机号", CustomerRules.maskMobile(profile.mobile()));
        body.put("备用电话", CustomerRules.maskMobile(profile.altMobile()));
        CustomerVo.Stats stats = detail.stats();
        body.put("消费", Map.of(
                "下单总金额分", stats.orderAmount(),
                "实收分", stats.receivedAmount(),
                "待付款分", stats.unpaidAmount(),
                "订单数", stats.orderCount(),
                "客单价分", stats.avgAmount() == null ? 0 : stats.avgAmount(),
                "首次购买", stats.firstOrderAt() == null ? "" : stats.firstOrderAt(),
                "最近购买", stats.lastOrderAt() == null ? "" : stats.lastOrderAt(),
                "距今天数", stats.daysSinceLast() == null ? "" : stats.daysSinceLast()
        ));
        body.put("买过的机型", detail.models().stream().limit(8).map(model -> Map.of(
                "型号", model.model(),
                "品牌", model.brand() == null ? "" : model.brand(),
                "次数", model.times(),
                "台数", model.quantity(),
                "金额分", model.amount(),
                "最后购买", model.lastAt() == null ? "" : model.lastAt()
        )).toList());
        CustomerVo.Preference preference = detail.preference();
        body.put("偏好", Map.of(
                "常买品牌", preference.autoBrand() == null ? "" : preference.autoBrand(),
                "常买品类", preference.autoCategory() == null ? "" : preference.autoCategory(),
                "价格区间", preference.autoPriceBand() == null ? "" : preference.autoPriceBand(),
                "常用付款方式", preference.autoPayMethod() == null ? "" : preference.autoPayMethod(),
                "常需上门安装", preference.autoNeedInstall() == null ? "不清楚" : (preference.autoNeedInstall() ? "是" : "否"),
                "老板补充", preference.manualNote() == null ? "" : preference.manualNote()
        ));
        long overdue = detail.agreements().stream().filter(row -> "OVERDUE".equals(row.bucket()) && "ACTIVE".equals(row.status())).count();
        body.put("协议", Map.of("生效份数", detail.agreements().stream().filter(row -> "ACTIVE".equals(row.status())).count(), "逾期份数", overdue));
        body.put("设备数", detail.assets().size());
        body.put("报修单数", detail.repairs().size());
        List<Map<String, String>> follows = new ArrayList<>();
        for (CustomerVo.Follow follow : detail.follows().stream().limit(8).toList()) {
            follows.add(Map.of(
                    "日期", follow.followDate() == null ? "" : follow.followDate(),
                    "内容", follow.content() == null ? "" : follow.content(),
                    "下次", follow.nextDate() == null ? "" : follow.nextDate()
            ));
        }
        body.put("跟进", follows);
        return write(body);
    }

    private String overallPrompt(CustomerVo.Overview overview) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("客户数", overview.customerCount());
        body.put("有待付款的客户数", overview.unpaidCustomerCount());
        body.put("待付款合计分", overview.unpaidAmount());
        body.put("逾期客户数", overview.overdueCount());
        body.put("沉睡客户数", overview.sleepingCount());
        body.put("等级分布", overview.levels());
        body.put("地区分布", overview.regions());
        body.put("机型排行", overview.models());
        body.put("待付款排行", overview.unpaidTop());
        return write(body);
    }

    private CustomerVo.Insight call(String kind, Long customerId, String prompt, String system) {
        try {
            String result = deepSeekClient.chat(system, prompt);
            if (result == null || result.isBlank()) {
                return new CustomerVo.Insight(true, false, "模型没有返回内容，请稍后再试。", null);
            }
            CustomerAnalysis row = new CustomerAnalysis();
            row.setCustomerId(customerId);
            row.setKind(kind);
            row.setPromptText(cut(prompt));
            row.setResultText(cut(result.trim()));
            row.setOperatorName(UserContext.require().getDisplayName());
            row.setCreatedAt(DateTimes.now());
            analysisMapper.insert(row);
            return new CustomerVo.Insight(true, true, "ok", toVo(row));
        } catch (DeepSeekException ex) {
            return new CustomerVo.Insight(true, false, ex.getMessage(), null);
        } catch (RuntimeException ex) {
            log.warn("客户分析失败: {}", ex.toString());
            return new CustomerVo.Insight(true, false, "分析服务暂时不可用，请稍后再试。", null);
        }
    }

    private CustomerVo.Analysis toVo(CustomerAnalysis row) {
        return new CustomerVo.Analysis(
                row.getId(),
                row.getCustomerId(),
                row.getKind(),
                row.getPromptText(),
                row.getResultText(),
                row.getOperatorName(),
                DateTimes.format(row.getCreatedAt())
        );
    }

    private static CustomerVo.Insight missing() {
        return new CustomerVo.Insight(false, false, MISSING_KEY, null);
    }

    private String write(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            return body.toString();
        }
    }

    private static String cut(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 8000 ? text : text.substring(0, 8000);
    }
}
