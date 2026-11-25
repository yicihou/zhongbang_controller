package com.zhongbang.controller.service.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.mapper.order.OrderLineMapper;
import com.zhongbang.controller.mapper.order.SalesOrderMapper;
import com.zhongbang.controller.mapper.payment.PaymentBillMapper;
import com.zhongbang.controller.mapper.product.ProductMapper;
import com.zhongbang.controller.mapper.product.ProductSkuMapper;
import com.zhongbang.controller.mapper.repair.RepairTicketMapper;
import com.zhongbang.controller.pojo.order.entity.OrderLine;
import com.zhongbang.controller.pojo.order.entity.SalesOrder;
import com.zhongbang.controller.pojo.payment.entity.PaymentBill;
import com.zhongbang.controller.pojo.product.entity.Product;
import com.zhongbang.controller.pojo.product.entity.ProductSku;
import com.zhongbang.controller.pojo.repair.entity.RepairTicket;
import com.zhongbang.controller.pojo.report.vo.ReportVo;
import com.zhongbang.controller.security.StaffRoles;
import com.zhongbang.controller.service.cost.CostPriceService;
import com.zhongbang.controller.service.cost.TradeMath;
import com.zhongbang.controller.service.stock.StockAlertService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.DateTimeException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 销售毛利月报和年报。数字都在这里算，不交给大模型。
 * 口径：ALL 全部，INVOICED 只看开票，UNINVOICED 只看不开票。
 * 未知成本的订单行不进商品成本和毛利。
 */
@Service
public class MarginReportService {

    private final SalesOrderMapper salesOrderMapper;
    private final OrderLineMapper orderLineMapper;
    private final RepairTicketMapper repairTicketMapper;
    private final PaymentBillMapper paymentBillMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final CostPriceService costPriceService;
    private final StockAlertService stockAlertService;
    private final JdbcTemplate jdbc;

    public MarginReportService(
            SalesOrderMapper salesOrderMapper,
            OrderLineMapper orderLineMapper,
            RepairTicketMapper repairTicketMapper,
            PaymentBillMapper paymentBillMapper,
            ProductMapper productMapper,
            ProductSkuMapper productSkuMapper,
            CostPriceService costPriceService,
            StockAlertService stockAlertService,
            JdbcTemplate jdbc
    ) {
        this.salesOrderMapper = salesOrderMapper;
        this.orderLineMapper = orderLineMapper;
        this.repairTicketMapper = repairTicketMapper;
        this.paymentBillMapper = paymentBillMapper;
        this.productMapper = productMapper;
        this.productSkuMapper = productSkuMapper;
        this.costPriceService = costPriceService;
        this.stockAlertService = stockAlertService;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public ReportVo.Report report(String periodType, String periodKey, String scope, String compareType, String compareKey) {
        StaffRoles.requireBoss("只有老板可以查看销售毛利");
        LocalDateTime now = DateTimes.now();
        LocalDate today = now.toLocalDate();
        ReportPeriods periods = reportPeriods(periodType, periodKey, compareType, compareKey, today);
        Period period = periods.current();
        Period previous = periods.comparison();
        Period yearAgo = "MONTH".equals(period.type()) ? shifted(period, 12, today) : null;
        String invoiceScope = scope(scope);
        ReportVo.Totals currentTotals = totalsFor(period, invoiceScope, now);
        ReportVo.Totals previousTotals = totalsFor(previous, invoiceScope, now);
        ReportVo.Totals yearAgoTotals = yearAgo == null ? null : totalsFor(yearAgo, invoiceScope, now);
        Map<String, Number> differences = period.type().equals(previous.type())
                && currentTotals.available() && previousTotals.available()
                ? differences(currentTotals, previousTotals) : Map.of();
        return new ReportVo.Report(period.type(), period.key(), invoiceScope, currentTotals, previousTotals,
                yearAgoTotals, previous.type(), differences);
    }

    private ReportVo.Totals totalsFor(Period period, String scope, LocalDateTime now) {
        ReportVo.Totals totals = totals(loadData(period.start(), period.end()), period, scope, now, false);
        if (totals.available()) {
            return totals;
        }
        Integer observed = jdbc.queryForObject("""
                SELECT COUNT(*) FROM margin_daily_snapshot
                WHERE period_type = 'MONTH' AND invoice_scope = 'ALL' AND report_date >= ? AND report_date < ?
                """, Integer.class, period.start().toLocalDate(), period.end().toLocalDate());
        return observed != null && observed == period.days() ? totals.withAvailability() : totals;
    }

    private static Map<String, Number> differences(ReportVo.Totals current, ReportVo.Totals previous) {
        Map<String, Number> values = new HashMap<>();
        values.put("salesAmount", current.salesAmount() - previous.salesAmount());
        values.put("receivedAmount", current.receivedAmount() - previous.receivedAmount());
        values.put("unpaidAmount", current.unpaidAmount() - previous.unpaidAmount());
        values.put("salesTaxIncluded", current.salesTaxIncluded() - previous.salesTaxIncluded());
        values.put("salesTaxExcluded", current.salesTaxExcluded() - previous.salesTaxExcluded());
        values.put("unknownSalesAmount", current.unknownSalesAmount() - previous.unknownSalesAmount());
        values.put("installIncome", current.installIncome() - previous.installIncome());
        values.put("repairIncome", current.repairIncome() - previous.repairIncome());
        if (current.goodsCost() != null && previous.goodsCost() != null) {
            values.put("goodsCost", current.goodsCost() - previous.goodsCost());
        }
        if (current.grossProfit() != null && previous.grossProfit() != null) {
            values.put("grossProfit", current.grossProfit() - previous.grossProfit());
        }
        if (current.grossMargin() != null && previous.grossMargin() != null) {
            values.put("grossMargin", new BigDecimal(current.grossMargin()).subtract(new BigDecimal(previous.grossMargin())));
        }
        return values;
    }

    @Transactional(readOnly = true)
    public ReportVo.Products products(String periodType, String periodKey, String scope, String compareType, String compareKey) {
        StaffRoles.requireBoss("只有老板可以查看销售毛利");
        Period period = reportPeriods(periodType, periodKey, compareType, compareKey, DateTimes.now().toLocalDate()).current();
        String invoiceScope = scope(scope);
        return new ReportVo.Products(period.type(), period.key(), invoiceScope, (int) period.days(), skuRows(period, invoiceScope));
    }

    @Transactional(readOnly = true)
    public byte[] export(String periodType, String periodKey, String scope, String compareType, String compareKey) {
        ReportVo.Report report = report(periodType, periodKey, scope, compareType, compareKey);
        ReportVo.Products products = products(periodType, periodKey, scope, compareType, compareKey);
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet summary = book.createSheet("汇总");
            int row = 0;
            row = writeTotals(summary, row, "本期 " + report.current().from() + " 至 " + report.current().through(), report.current());
            row = writeTotals(summary, row + 1, "对比 " + report.previous().from() + " 至 " + report.previous().through(), report.previous());
            if (report.yearAgo() != null) {
                writeTotals(summary, row + 1, "去年同期 " + report.yearAgo().periodKey(), report.yearAgo());
            }
            Sheet sheet = book.createSheet("规格");
            String[] headers = {"商品", "型号", "规格", "销量", "销售额", "不含税销售额", "成本", "毛利", "毛利率", "未知成本行", "库存", "周转天数", "畅销", "滞销", "建议采购"};
            Row head = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                head.createCell(i).setCellValue(headers[i]);
            }
            int index = 1;
            for (ReportVo.SkuRow item : products.list()) {
                Row line = sheet.createRow(index++);
                int col = 0;
                line.createCell(col++).setCellValue(text(item.productName()));
                line.createCell(col++).setCellValue(text(item.model()));
                line.createCell(col++).setCellValue(text(item.skuName()));
                line.createCell(col++).setCellValue(item.quantity());
                line.createCell(col++).setCellValue(yuan(item.salesAmount()));
                line.createCell(col++).setCellValue(yuan(item.salesTaxExcluded()));
                line.createCell(col++).setCellValue(item.costAmount() == null ? "未知成本" : yuan(item.costAmount()));
                line.createCell(col++).setCellValue(item.grossProfit() == null ? "未知成本" : yuan(item.grossProfit()));
                line.createCell(col++).setCellValue(item.grossMargin() == null ? "" : item.grossMargin() + "%");
                line.createCell(col++).setCellValue(item.unknownLineCount());
                line.createCell(col++).setCellValue(item.stock());
                line.createCell(col++).setCellValue(item.turnoverDays() == null ? "" : item.turnoverDays().toString());
                line.createCell(col++).setCellValue(item.hot() ? "畅销" : "");
                line.createCell(col++).setCellValue(item.slow() ? "滞销" : "");
                line.createCell(col).setCellValue(item.suggestQty());
            }
            book.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "导出失败");
        }
    }

    private int writeTotals(Sheet sheet, int start, String title, ReportVo.Totals totals) {
        String[][] rows = {
                {title, ""},
                {"销售额", yuan(totals.salesAmount())},
                {"销售额含税", yuan(totals.salesTaxIncluded())},
                {"销售额不含税", yuan(totals.salesTaxExcluded())},
                {"实收", yuan(totals.receivedAmount())},
                {"实收含税", yuan(totals.receivedTaxIncluded())},
                {"实收不含税", yuan(totals.receivedTaxExcluded())},
                {"待收", yuan(totals.unpaidAmount())},
                {"商品成本", totals.goodsCost() == null ? "未知成本" : yuan(totals.goodsCost())},
                {"毛利", totals.grossProfit() == null ? "未知成本" : yuan(totals.grossProfit())},
                {"毛利率", totals.grossMargin() == null ? "" : totals.grossMargin() + "%"},
                {"未知成本销售额", yuan(totals.unknownSalesAmount())},
                {"安装费收入", yuan(totals.installIncome())},
                {"维修收入", yuan(totals.repairIncome())}
        };
        int cursor = start;
        for (String[] pair : rows) {
            Row row = sheet.createRow(cursor++);
            row.createCell(0).setCellValue(pair[0]);
            row.createCell(1).setCellValue(pair[1]);
        }
        return cursor;
    }

    ReportVo.Totals totals(ReportData data, Period period, String scope, LocalDateTime now, boolean original) {
        LocalDateTime cutoff = period.end().isBefore(now) ? period.end() : now;
        List<SalesOrder> activeOrders = data.orders().stream()
                .filter(order -> contains(period, cutoff, order.getCreatedAt()))
                .filter(order -> wasActive(order.getStatus(), order.getUpdatedAt(), cutoff))
                .toList();
        List<RepairTicket> activeRepairs = data.repairs().stream()
                .filter(ticket -> contains(period, cutoff, ticket.getCreatedAt()))
                .filter(ticket -> wasActive(ticket.getStatus(), ticket.getUpdatedAt(), cutoff))
                .toList();
        List<SalesOrder> orders = activeOrders.stream().filter(order -> matchScope(scope, invoiced(order))).toList();
        long sales = 0;
        long salesIn = 0;
        long salesEx = 0;
        long unknownSales = 0;
        int unknownLines = 0;
        long knownNet = 0;
        long cost = 0;
        boolean anyKnown = false;
        for (SalesOrder order : orders) {
            for (OrderLine line : data.lines().getOrDefault(order.getId(), List.of())) {
                long amount = line.getLineAmount() == null ? 0L : line.getLineAmount();
                TradeMath.TaxParts parts = TradeMath.split(amount, invoiced(order), order.getTaxIncluded(), order.getTaxRate());
                sales += amount;
                salesIn += parts.included();
                salesEx += parts.excluded();
                if (line.getCostKnown() != null && line.getCostKnown() == 1 && line.getCostAmount() != null) {
                    anyKnown = true;
                    knownNet += parts.excluded();
                    cost += line.getCostAmount();
                } else {
                    unknownSales += amount;
                    unknownLines += 1;
                }
            }
        }
        long received = 0;
        long receivedIn = 0;
        long receivedEx = 0;
        long unpaid = 0;
        long install = 0;
        long installIn = 0;
        long installEx = 0;
        for (SalesOrder order : orders) {
            long paid = paidBefore(data.orderPayments(), order.getId(), cutoff, original);
            long payable = order.getPayableAmount() == null ? 0L : order.getPayableAmount();
            TradeMath.TaxParts paidParts = TradeMath.split(paid, invoiced(order), order.getTaxIncluded(), order.getTaxRate());
            received += paid;
            receivedIn += paidParts.included();
            receivedEx += paidParts.excluded();
            unpaid += Math.max(0, payable - paid);
            long installAmount = order.getInstallAmount() == null ? 0L : order.getInstallAmount();
            TradeMath.TaxParts installParts = TradeMath.split(installAmount, invoiced(order), order.getTaxIncluded(), order.getTaxRate());
            install += installAmount;
            installIn += installParts.included();
            installEx += installParts.excluded();
        }
        long repair = 0;
        long repairIn = 0;
        long repairEx = 0;
        for (RepairTicket ticket : activeRepairs) {
            boolean invoice = ticket.getInvoiced() != null && ticket.getInvoiced() == 1;
            if (!matchScope(scope, invoice)) {
                continue;
            }
            long paid = paidBefore(data.repairPayments(), ticket.getId(), cutoff, original);
            TradeMath.TaxParts parts = TradeMath.split(paid, invoice, ticket.getTaxIncluded(), ticket.getTaxRate());
            repair += paid;
            repairIn += parts.included();
            repairEx += parts.excluded();
        }
        Long goodsCost = anyKnown ? cost : null;
        Long profit = anyKnown ? knownNet - cost : null;
        String margin = profit == null ? null : TradeMath.marginText(profit, knownNet);
        return new ReportVo.Totals(
                period.key(), sales, salesIn, salesEx, received, receivedIn, receivedEx, unpaid,
                goodsCost, profit, margin, unknownSales, unknownLines,
                install, installIn, installEx, repair, repairIn, repairEx,
                period.start().toLocalDate().toString(), period.end().toLocalDate().minusDays(1).toString(),
                period.end().isAfter(now) || !activeOrders.isEmpty() || !activeRepairs.isEmpty()
        );
    }

    private List<ReportVo.SkuRow> skuRows(Period period, String scope) {
        LocalDateTime now = DateTimes.now();
        LocalDateTime cutoff = period.end().isBefore(now) ? period.end() : now;
        List<SalesOrder> orders = orders(period.start(), cutoff).stream()
                .filter(order -> wasActive(order.getStatus(), order.getUpdatedAt(), cutoff))
                .filter(order -> matchScope(scope, invoiced(order)))
                .toList();
        Map<Long, SalesOrder> byId = new HashMap<>();
        for (SalesOrder order : orders) {
            byId.put(order.getId(), order);
        }
        Map<Long, MutableSku> grouped = new HashMap<>();
        for (OrderLine line : linesOf(byId.keySet())) {
            MutableSku row = grouped.computeIfAbsent(line.getSkuId(), MutableSku::new);
            SalesOrder order = byId.get(line.getOrderId());
            long amount = line.getLineAmount() == null ? 0L : line.getLineAmount();
            TradeMath.TaxParts parts = TradeMath.split(amount, invoiced(order), order.getTaxIncluded(), order.getTaxRate());
            row.quantity += line.getQuantity() == null ? 0 : line.getQuantity();
            row.sales += amount;
            row.salesEx += parts.excluded();
            if (line.getCostKnown() != null && line.getCostKnown() == 1 && line.getCostAmount() != null) {
                row.known = true;
                row.knownNet += parts.excluded();
                row.cost += line.getCostAmount();
            } else {
                row.unknown += 1;
            }
            if (row.name == null) {
                row.name = line.getSkuName();
                row.productName = line.getProductName();
            }
        }
        Map<Long, Integer> transit = stockAlertService.inTransit();
        int hotDays = costPriceService.setting().getHotTurnoverDays() == null ? 30 : costPriceService.setting().getHotTurnoverDays();
        int slowDays = costPriceService.setting().getSlowTurnoverDays() == null ? 90 : costPriceService.setting().getSlowTurnoverDays();
        int multiplier = costPriceService.setting().getSuggestMultiplier() == null ? 2 : costPriceService.setting().getSuggestMultiplier();
        List<ProductSku> skus = productSkuMapper.selectList(new LambdaQueryWrapper<ProductSku>().orderByAsc(ProductSku::getId));
        List<ReportVo.SkuRow> rows = new ArrayList<>();
        for (ProductSku sku : skus) {
            MutableSku row = grouped.get(sku.getId());
            int stock = sku.getStock() == null ? 0 : sku.getStock();
            if (row == null && stock <= 0) {
                continue;
            }
            if (row == null) {
                row = new MutableSku(sku.getId());
            }
            Product product = productMapper.selectById(sku.getProductId());
            int min = costPriceService.minStockOf(sku);
            Integer turnover = TradeMath.turnoverDays(stock, row.quantity, period.days());
            boolean hot = row.quantity > 0 && turnover != null && turnover <= hotDays;
            boolean slow = stock > 0 && (row.quantity == 0 || (turnover != null && turnover > slowDays));
            Long cost = row.known ? row.cost : null;
            Long profit = row.known ? row.knownNet - row.cost : null;
            rows.add(new ReportVo.SkuRow(
                    sku.getId(),
                    sku.getProductId(),
                    product == null ? row.productName : product.getName(),
                    product == null ? null : product.getModel(),
                    sku.getTitle(),
                    row.quantity,
                    row.sales,
                    row.salesEx,
                    cost,
                    profit,
                    profit == null ? null : TradeMath.marginText(profit, row.knownNet),
                    row.unknown,
                    stock,
                    turnover,
                    hot,
                    slow,
                    TradeMath.suggest(min, multiplier, stock, transit.getOrDefault(sku.getId(), 0))
            ));
        }
        rows.sort(Comparator.comparingInt(ReportVo.SkuRow::quantity).reversed());
        return rows;
    }

    ReportData loadData(LocalDateTime start, LocalDateTime end) {
        List<SalesOrder> orders = orders(start, end);
        List<RepairTicket> tickets = repairTicketMapper.selectList(new LambdaQueryWrapper<RepairTicket>()
                .ge(RepairTicket::getCreatedAt, start).lt(RepairTicket::getCreatedAt, end));
        List<Long> orderIds = orders.stream().map(SalesOrder::getId).toList();
        List<Long> repairIds = tickets.stream().map(RepairTicket::getId).toList();
        return new ReportData(orders, tickets, group(linesOf(orderIds), OrderLine::getOrderId),
                group(payments("ORDER", orderIds, end), MarginReportService::paymentOwner),
                group(payments("REPAIR", repairIds, end), MarginReportService::paymentOwner));
    }

    private List<SalesOrder> orders(LocalDateTime start, LocalDateTime end) {
        return salesOrderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                .ge(SalesOrder::getCreatedAt, start).lt(SalesOrder::getCreatedAt, end));
    }

    private List<OrderLine> linesOf(java.util.Collection<Long> ids) {
        return inBatches(ids, batch -> orderLineMapper.selectList(new LambdaQueryWrapper<OrderLine>()
                .in(OrderLine::getOrderId, batch)));
    }

    private List<PaymentBill> payments(String biz, List<Long> ids, LocalDateTime end) {
        return inBatches(ids, batch -> paymentBillMapper.selectList(new LambdaQueryWrapper<PaymentBill>()
                .eq(PaymentBill::getBizType, biz).eq(PaymentBill::getStatus, "SUCCESS")
                .lt(PaymentBill::getPaidAt, end)
                .and(owner -> owner.in(PaymentBill::getBizId, batch)
                        .or(legacy -> legacy.isNull(PaymentBill::getBizId).in(PaymentBill::getOrderId, batch)))));
    }

    private static <T> List<T> inBatches(java.util.Collection<Long> ids, Function<List<Long>, List<T>> query) {
        List<Long> list = List.copyOf(ids);
        List<T> result = new ArrayList<>();
        for (int from = 0; from < list.size(); from += 500) {
            result.addAll(query.apply(list.subList(from, Math.min(from + 500, list.size()))));
        }
        return result;
    }

    private static <T> Map<Long, List<T>> group(List<T> rows, Function<T, Long> key) {
        return rows.stream().collect(Collectors.groupingBy(key));
    }

    private static Long paymentOwner(PaymentBill row) {
        return row.getBizId() == null ? row.getOrderId() : row.getBizId();
    }

    private static long paidBefore(Map<Long, List<PaymentBill>> byOwner, Long id, LocalDateTime cutoff, boolean original) {
        return byOwner.getOrDefault(id, List.of()).stream()
                .filter(row -> row.getPaidAt().isBefore(cutoff))
                .filter(row -> !original || row.getRegisteredAt() == null || row.getRegisteredAt().isBefore(cutoff))
                .map(PaymentBill::getAmount).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
    }

    private static boolean contains(Period period, LocalDateTime cutoff, LocalDateTime createdAt) {
        return !createdAt.isBefore(period.start()) && createdAt.isBefore(cutoff);
    }

    private static boolean wasActive(String status, LocalDateTime updatedAt, LocalDateTime cutoff) {
        return !"CANCELLED".equals(status) || updatedAt != null && !updatedAt.isBefore(cutoff);
    }

    record ReportData(List<SalesOrder> orders, List<RepairTicket> repairs, Map<Long, List<OrderLine>> lines,
                      Map<Long, List<PaymentBill>> orderPayments, Map<Long, List<PaymentBill>> repairPayments) {
    }

    private static boolean invoiced(SalesOrder order) {
        return order.getInvoiced() != null && order.getInvoiced() == 1;
    }

    private static boolean matchScope(String scope, boolean invoiced) {
        return switch (scope) {
            case "INVOICED" -> invoiced;
            case "UNINVOICED" -> !invoiced;
            default -> true;
        };
    }

    public static String scope(String scope) {
        if (scope == null || scope.isBlank() || "ALL".equalsIgnoreCase(scope)) {
            return "ALL";
        }
        if ("INVOICED".equalsIgnoreCase(scope)) {
            return "INVOICED";
        }
        if ("UNINVOICED".equalsIgnoreCase(scope)) {
            return "UNINVOICED";
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "口径只能是全部、开票或不开发票");
    }

    static Period period(String periodType, String periodKey, LocalDate today) {
        String type = periodType == null || periodType.isBlank() ? "MONTH" : periodType.trim().toUpperCase(java.util.Locale.ROOT);
        String key = periodKey == null ? "" : periodKey.trim();
        try {
            LocalDate start = switch (type) {
                case "MONTH" -> (key.isEmpty() ? YearMonth.from(today) : YearMonth.parse(key)).atDay(1);
                case "YEAR" -> LocalDate.of(key.isEmpty() ? today.getYear() : java.time.Year.parse(key).getValue(), 1, 1);
                default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "周期只能是月或年");
            };
            if (start.isAfter(today) || start.getYear() < 1) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择有效的报表期间");
            }
            LocalDate end = min(naturalEnd(type, start), today.plusDays(1));
            return range(type, start, end);
        } catch (DateTimeException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择有效的报表期间");
        }
    }

    static Period range(String type, LocalDate start, LocalDate end) {
        String key = "YEAR".equals(type) ? Integer.toString(start.getYear()) : YearMonth.from(start).toString();
        return new Period(type, key, start.atStartOfDay(), end.atStartOfDay());
    }

    private static ReportPeriods reportPeriods(String type, String key, String compareType, String compareKey, LocalDate today) {
        Period current = period(type, key, today);
        Period previous = comparison(current, compareType, compareKey, today);
        if (current.type().equals(previous.type()) && previous.end().toLocalDate().equals(today.plusDays(1))) {
            current = align(previous, current, today);
        }
        return new ReportPeriods(current, previous);
    }

    private record ReportPeriods(Period current, Period comparison) {
    }

    private static Period comparison(Period period, String compareType, String compareKey, LocalDate today) {
        if (compareKey == null || compareKey.isBlank()) {
            return shifted(period, "YEAR".equals(period.type()) ? 12 : 1, today);
        }
        Period candidate = period(compareType == null ? period.type() : compareType, compareKey, today);
        return align(period, candidate, today);
    }

    private static Period shifted(Period period, int months, LocalDate today) {
        LocalDate start = period.start().toLocalDate().minusMonths(months);
        return align(period, range(period.type(), start, naturalEnd(period.type(), start)), today);
    }

    private static Period align(Period period, Period candidate, LocalDate today) {
        LocalDate start = candidate.start().toLocalDate();
        LocalDate end = min(candidate.end().toLocalDate(), today.plusDays(1));
        if (period.type().equals(candidate.type())) {
            LocalDate currentStart = period.start().toLocalDate();
            LocalDate currentEnd = period.end().toLocalDate();
            if (currentEnd.equals(today.plusDays(1)) || currentEnd.isBefore(naturalEnd(period.type(), currentStart))) {
                LocalDate through = currentEnd.minusDays(1);
                LocalDate aligned = "YEAR".equals(period.type())
                        ? through.withYear(start.getYear())
                        : start.withDayOfMonth(Math.min(through.getDayOfMonth(), start.lengthOfMonth()));
                end = min(end, aligned.plusDays(1));
            }
        }
        return range(candidate.type(), start, end);
    }

    private static LocalDate naturalEnd(String type, LocalDate start) {
        return "YEAR".equals(type) ? start.plusYears(1) : start.plusMonths(1);
    }

    static LocalDate min(LocalDate first, LocalDate second) {
        return first.isBefore(second) ? first : second;
    }

    private static String yuan(long fen) {
        boolean negative = fen < 0;
        long abs = Math.abs(fen);
        return (negative ? "-" : "") + (abs / 100) + "." + String.format("%02d", abs % 100);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    public record Period(String type, String key, LocalDateTime start, LocalDateTime end) {
        long days() {
            return Math.max(1, ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate()));
        }
    }

    private static final class MutableSku {
        private final Long skuId;
        private String productName;
        private String name;
        private int quantity;
        private long sales;
        private long salesEx;
        private long knownNet;
        private long cost;
        private int unknown;
        private boolean known;

        private MutableSku(Long skuId) {
            this.skuId = skuId;
        }
    }
}
