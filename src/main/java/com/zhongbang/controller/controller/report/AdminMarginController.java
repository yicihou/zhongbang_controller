package com.zhongbang.controller.controller.report;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.pojo.report.vo.ReportVo;
import com.zhongbang.controller.service.report.MarginHistoryService;
import com.zhongbang.controller.service.report.MarginInsightService;
import com.zhongbang.controller.service.report.MarginReportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 销售毛利月报、年报和 DeepSeek 解读。只有老板能看。
 * period=MONTH 时 key 写成 2026-11；period=YEAR 时 key 写成 2026。
 * scope：ALL 全部，INVOICED 开票，UNINVOICED 不开票。
 */
@RestController
@RequestMapping("/api/admin/reports/margin")
@PreAuthorize("hasRole('BOSS')")
public class AdminMarginController {

    private final MarginReportService marginReportService;
    private final MarginInsightService marginInsightService;
    private final MarginHistoryService marginHistoryService;

    public AdminMarginController(MarginReportService marginReportService, MarginInsightService marginInsightService,
                                 MarginHistoryService marginHistoryService) {
        this.marginReportService = marginReportService;
        this.marginInsightService = marginInsightService;
        this.marginHistoryService = marginHistoryService;
    }

    @GetMapping
    public ApiResponse<ReportVo.Report> report(
            @RequestParam(defaultValue = "MONTH") String period,
            @RequestParam(required = false) String key,
            @RequestParam(defaultValue = "ALL") String scope,
            @RequestParam(required = false) String comparePeriod,
            @RequestParam(required = false) String compareKey
    ) {
        return ApiResponse.ok(marginReportService.report(period, key, scope, comparePeriod, compareKey));
    }

    @GetMapping("/products")
    public ApiResponse<ReportVo.Products> products(
            @RequestParam(defaultValue = "MONTH") String period,
            @RequestParam(required = false) String key,
            @RequestParam(defaultValue = "ALL") String scope,
            @RequestParam(required = false) String comparePeriod,
            @RequestParam(required = false) String compareKey
    ) {
        return ApiResponse.ok(marginReportService.products(period, key, scope, comparePeriod, compareKey));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "MONTH") String period,
            @RequestParam(required = false) String key,
            @RequestParam(defaultValue = "ALL") String scope,
            @RequestParam(required = false) String comparePeriod,
            @RequestParam(required = false) String compareKey
    ) {
        byte[] body = marginReportService.export(period, key, scope, comparePeriod, compareKey);
        String filename = "毛利报表.xlsx";
        ContentDisposition disposition = ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @GetMapping("/history")
    public ApiResponse<ReportVo.History> reportHistory(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String through,
            @RequestParam(defaultValue = "ALL") String scope
    ) {
        return ApiResponse.ok(marginHistoryService.history(month, from, through, scope));
    }

    @PostMapping("/insights")
    public ApiResponse<ReportVo.Insight> insight(@RequestBody(required = false) Map<String, String> body) {
        Map<String, String> query = body == null ? Map.of() : body;
        return ApiResponse.ok(marginInsightService.analyze(query.get("period"), query.get("key"), query.get("scope"),
                query.get("comparePeriod"), query.get("compareKey")));
    }

    @GetMapping("/insights")
    public ApiResponse<List<ReportVo.Analysis>> history(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String key,
            @RequestParam(required = false) String scope
    ) {
        return ApiResponse.ok(marginInsightService.history(period, key, scope));
    }
}
