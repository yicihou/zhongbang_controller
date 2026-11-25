package com.zhongbang.controller.service.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.pojo.report.vo.ReportVo;
import com.zhongbang.controller.security.StaffRoles;
import com.zhongbang.controller.service.report.MarginReportService.Period;
import com.zhongbang.controller.service.report.MarginReportService.ReportData;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static com.zhongbang.controller.service.report.MarginReportService.min;
import static com.zhongbang.controller.service.report.MarginReportService.range;

@Service
public class MarginHistoryService {
    private static final List<String> SCOPES = List.of("ALL", "INVOICED", "UNINVOICED");

    private final MarginReportService reports;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public MarginHistoryService(MarginReportService reports, JdbcTemplate jdbc, ObjectMapper json) {
        this.reports = reports;
        this.jdbc = jdbc;
        this.json = json;
    }

    @Scheduled(cron = "${zhongbang.report.snapshot-cron:30 0 0 * * *}", zone = "Asia/Shanghai")
    @Transactional
    public void captureYesterday() {
        capture(DateTimes.now().toLocalDate().minusDays(1));
    }

    void capture(LocalDate date) {
        LocalDateTime recordedAt = DateTimes.now();
        LocalDate end = date.plusDays(1);
        LocalDate yearStart = date.withDayOfYear(1);
        ReportData data = reports.loadData(yearStart.atStartOfDay(), end.atStartOfDay());
        for (Period period : List.of(range("MONTH", date.withDayOfMonth(1), end), range("YEAR", yearStart, end))) {
            for (String scope : SCOPES) {
                ReportVo.Totals totals = reports.totals(data, period, scope, recordedAt, true);
                try {
                    jdbc.update("""
                            INSERT INTO margin_daily_snapshot (report_date, period_type, invoice_scope, recorded_at, totals_json)
                            VALUES (?, ?, ?, ?, ?)
                            """, date, period.type(), scope, recordedAt, json.writeValueAsString(totals));
                } catch (DuplicateKeyException ignored) {
                    // 保留这个日期第一次写入的结果，重复执行不会覆盖。
                } catch (JsonProcessingException ex) {
                    throw new IllegalStateException("无法保存毛利报表", ex);
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public ReportVo.History history(String monthKey, String fromText, String throughText, String invoiceScope) {
        StaffRoles.requireBoss("只有老板可以查看经营分析");
        LocalDateTime now = DateTimes.now();
        LocalDate today = now.toLocalDate();
        String scope = MarginReportService.scope(invoiceScope);
        Period selected = MarginReportService.period("MONTH", monthKey, today);
        LocalDate from = parseDate(fromText, today.withDayOfYear(1));
        LocalDate through = parseDate(throughText, today);
        if (from.isAfter(through) || through.isAfter(today)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择有效的日期范围");
        }

        LocalDate selectedStart = selected.start().toLocalDate();
        LocalDate selectedEnd = selected.end().toLocalDate();
        LocalDate dataStart = min(from, selectedStart);
        LocalDate rangeEnd = through.plusDays(1);
        LocalDate dataEnd = rangeEnd.isAfter(selectedEnd) ? rangeEnd : selectedEnd;
        ReportData data = reports.loadData(dataStart.atStartOfDay(), dataEnd.atStartOfDay());
        Set<LocalDate> snapshots = new HashSet<>(jdbc.query("""
                SELECT report_date FROM margin_daily_snapshot
                WHERE period_type = 'MONTH' AND invoice_scope = 'ALL' AND report_date >= ? AND report_date < ?
                """, (row, index) -> row.getObject(1, LocalDate.class), dataStart, dataEnd));

        List<Period> baseline = new ArrayList<>();
        List<ReportVo.TrendValues> baselineValues = new ArrayList<>();
        List<ReportVo.TrendPoint> months = new ArrayList<>();
        for (YearMonth month = YearMonth.from(from); !month.atDay(1).isAfter(through); month = month.plusMonths(1)) {
            LocalDate first = month.atDay(1);
            LocalDate naturalEnd = month.plusMonths(1).atDay(1);
            LocalDate start = first.isBefore(from) ? from : first;
            LocalDate end = min(naturalEnd, rangeEnd);
            Period period = range("MONTH", start, end);
            ReportVo.Totals totals = reports.totals(data, period, scope, now, false);
            boolean known = totals.available() || !end.isBefore(today.plusDays(1)) || covered(snapshots, start, end);
            ReportVo.TrendValues values = known ? values(totals) : null;
            months.add(new ReportVo.TrendPoint(month.toString(), values, null));
            if (values != null && start.equals(first) && end.equals(naturalEnd) && !end.isAfter(today)) {
                baselineValues.add(values);
                if (!first.equals(selectedStart)) {
                    baseline.add(period);
                }
            }
        }

        ReportVo.TrendValues monthlyAverage = mean(baselineValues);
        ReportVo.Totals selectedTotals = reports.totals(data, selected, scope, now, false);
        boolean selectedKnown = selectedTotals.available() || selectedEnd.equals(today.plusDays(1))
                || covered(snapshots, selectedStart, selectedEnd);
        List<ReportVo.TrendPoint> days = new ArrayList<>();
        for (LocalDate day = selectedStart; day.isBefore(selectedEnd); day = day.plusDays(1)) {
            int dayOfMonth = day.getDayOfMonth();
            Period partial = range("MONTH", selectedStart, day.plusDays(1));
            ReportVo.TrendValues actual = selectedKnown ? values(reports.totals(data, partial, scope, now, false)) : null;
            List<ReportVo.TrendValues> samples = new ArrayList<>();
            for (Period month : baseline) {
                LocalDate begin = month.start().toLocalDate();
                if (dayOfMonth <= begin.lengthOfMonth()) {
                    LocalDate end = begin.withDayOfMonth(dayOfMonth).plusDays(1);
                    samples.add(values(reports.totals(data, range("MONTH", begin, end), scope, now, false)));
                }
            }
            days.add(new ReportVo.TrendPoint(day.toString(), actual, mean(samples)));
        }
        return new ReportVo.History(months, days, monthlyAverage);
    }

    private static boolean covered(Set<LocalDate> snapshots, LocalDate start, LocalDate end) {
        return start.datesUntil(end).allMatch(snapshots::contains);
    }

    private static ReportVo.TrendValues values(ReportVo.Totals totals) {
        return new ReportVo.TrendValues(totals.salesAmount(), totals.receivedAmount(), totals.unpaidAmount(), totals.grossProfit());
    }

    private static ReportVo.TrendValues mean(List<ReportVo.TrendValues> values) {
        if (values.isEmpty()) {
            return null;
        }
        return new ReportVo.TrendValues(mean(values, ReportVo.TrendValues::salesAmount),
                mean(values, ReportVo.TrendValues::receivedAmount), mean(values, ReportVo.TrendValues::unpaidAmount),
                mean(values, ReportVo.TrendValues::grossProfit));
    }

    private static Long mean(List<ReportVo.TrendValues> values, Function<ReportVo.TrendValues, Long> metric) {
        var average = values.stream().map(metric).filter(java.util.Objects::nonNull).mapToLong(Long::longValue).average();
        return average.isPresent() ? Math.round(average.getAsDouble()) : null;
    }

    private static LocalDate parseDate(String value, LocalDate fallback) {
        try {
            return value == null || value.isBlank() ? fallback : LocalDate.parse(value);
        } catch (DateTimeException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择有效日期");
        }
    }
}
