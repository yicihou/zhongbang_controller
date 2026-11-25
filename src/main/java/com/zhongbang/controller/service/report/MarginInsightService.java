package com.zhongbang.controller.service.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhongbang.controller.adapter.deepseek.DeepSeekClient;
import com.zhongbang.controller.adapter.deepseek.DeepSeekException;
import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.config.ZhongbangProperties;
import com.zhongbang.controller.mapper.report.MarginInsightMapper;
import com.zhongbang.controller.pojo.report.entity.MarginInsight;
import com.zhongbang.controller.pojo.report.vo.ReportVo;
import com.zhongbang.controller.security.StaffRoles;
import com.zhongbang.controller.security.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把已经算好的毛利数字交给 DeepSeek，只让它写中文解读和建议。
 * 模型不参与加减。没配 DEEPSEEK_API_KEY 时返回说明，不报 500。
 */
@Service
public class MarginInsightService {

    private static final Logger log = LoggerFactory.getLogger(MarginInsightService.class);
    private static final String MISSING_KEY = "还没有配置 DeepSeek。请在启动环境里设置 DEEPSEEK_API_KEY 后再试。";
    private static final String SYSTEM = """
            你是缝纫机经销店的经营助手。用户给你的数字是系统已经算好的，不要改数字，也不要自己重新计算。
            用中文写五段：销售概况、毛利（未知成本要单独说明，不能当成 0）、开票与不含税、库存里的畅销和滞销、下一步建议。
            不要写净利润，不要编造开支。每段不超过 120 字。
            """;

    private final MarginReportService reportService;
    private final MarginInsightMapper marginInsightMapper;
    private final DeepSeekClient deepSeekClient;
    private final ZhongbangProperties properties;
    private final ObjectMapper objectMapper;

    public MarginInsightService(
            MarginReportService reportService,
            MarginInsightMapper marginInsightMapper,
            DeepSeekClient deepSeekClient,
            ZhongbangProperties properties,
            ObjectMapper objectMapper
    ) {
        this.reportService = reportService;
        this.marginInsightMapper = marginInsightMapper;
        this.deepSeekClient = deepSeekClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public ReportVo.Insight analyze(String periodType, String periodKey, String scope) {
        StaffRoles.requireBoss("只有老板可以生成毛利解读");
        ReportVo.Report report = reportService.report(periodType, periodKey, scope);
        ReportVo.Products products = reportService.products(periodType, periodKey, scope);
        if (!properties.getDeepseek().configured()) {
            return new ReportVo.Insight(false, false, MISSING_KEY, null);
        }
        String prompt = prompt(report, products);
        try {
            String result = deepSeekClient.chat(SYSTEM, prompt);
            if (result == null || result.isBlank()) {
                return new ReportVo.Insight(true, false, "模型没有返回内容，请稍后再试。", null);
            }
            MarginInsight row = new MarginInsight();
            row.setPeriodType(report.periodType());
            row.setPeriodKey(report.periodKey());
            row.setInvoiceScope(report.scope());
            row.setNumbersJson(cut(prompt));
            row.setResultText(cut(result.trim()));
            row.setOperatorName(UserContext.require().getDisplayName());
            row.setCreatedAt(DateTimes.now());
            marginInsightMapper.insert(row);
            return new ReportVo.Insight(true, true, "ok", toVo(row));
        } catch (DeepSeekException ex) {
            return new ReportVo.Insight(true, false, ex.getMessage(), null);
        } catch (RuntimeException ex) {
            log.warn("毛利解读失败: {}", ex.toString());
            return new ReportVo.Insight(true, false, "分析服务暂时不可用，请稍后再试。", null);
        }
    }

    public List<ReportVo.Analysis> history(String periodType, String periodKey, String scope) {
        StaffRoles.requireBoss("只有老板可以查看毛利解读");
        LambdaQueryWrapper<MarginInsight> wrapper = new LambdaQueryWrapper<MarginInsight>()
                .orderByDesc(MarginInsight::getCreatedAt)
                .last("limit 20");
        if (periodType != null && !periodType.isBlank()) {
            wrapper.eq(MarginInsight::getPeriodType, periodType.trim().toUpperCase());
        }
        if (periodKey != null && !periodKey.isBlank()) {
            wrapper.eq(MarginInsight::getPeriodKey, periodKey.trim());
        }
        if (scope != null && !scope.isBlank()) {
            wrapper.eq(MarginInsight::getInvoiceScope, MarginReportService.scope(scope));
        }
        return marginInsightMapper.selectList(wrapper).stream().map(this::toVo).toList();
    }

    private String prompt(ReportVo.Report report, ReportVo.Products products) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("周期", report.periodType() + " " + report.periodKey());
        body.put("口径", report.scope());
        body.put("本期", numbers(report.current()));
        body.put("上期", numbers(report.previous()));
        if (report.yearAgo() != null) {
            body.put("去年同期", numbers(report.yearAgo()));
        }
        body.put("规格", products.list().stream().limit(12).map(row -> Map.of(
                "名称", row.productName() + " " + row.skuName(),
                "销量", row.quantity(),
                "销售额分", row.salesAmount(),
                "成本分", row.costAmount() == null ? "未知" : row.costAmount(),
                "毛利分", row.grossProfit() == null ? "未知" : row.grossProfit(),
                "库存", row.stock(),
                "周转天数", row.turnoverDays() == null ? "无销量" : row.turnoverDays(),
                "标签", row.hot() ? "畅销" : row.slow() ? "滞销" : ""
        )).toList());
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            return body.toString();
        }
    }

    private static Map<String, Object> numbers(ReportVo.Totals totals) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("期间", totals.periodKey());
        map.put("销售额分", totals.salesAmount());
        map.put("含税销售额分", totals.salesTaxIncluded());
        map.put("不含税销售额分", totals.salesTaxExcluded());
        map.put("实收分", totals.receivedAmount());
        map.put("待收分", totals.unpaidAmount());
        map.put("商品成本分", totals.goodsCost() == null ? "未知" : totals.goodsCost());
        map.put("毛利分", totals.grossProfit() == null ? "未知" : totals.grossProfit());
        map.put("毛利率", totals.grossMargin() == null ? "未知" : totals.grossMargin());
        map.put("未知成本销售额分", totals.unknownSalesAmount());
        map.put("安装费收入分", totals.installIncome());
        map.put("维修收入分", totals.repairIncome());
        return map;
    }

    private ReportVo.Analysis toVo(MarginInsight row) {
        return new ReportVo.Analysis(
                row.getId(),
                row.getPeriodType(),
                row.getPeriodKey(),
                row.getInvoiceScope(),
                row.getNumbersJson(),
                row.getResultText(),
                row.getOperatorName(),
                DateTimes.format(row.getCreatedAt())
        );
    }

    private static String cut(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 8000 ? text : text.substring(0, 8000);
    }
}
