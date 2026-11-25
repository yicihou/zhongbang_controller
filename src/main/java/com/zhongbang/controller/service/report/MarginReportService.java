package com.zhongbang.controller.service.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.mapper.order.OrderLineMapper;
import com.zhongbang.controller.mapper.order.SalesOrderMapper;
import com.zhongbang.controller.mapper.product.ProductMapper;
import com.zhongbang.controller.mapper.product.ProductSkuMapper;
import com.zhongbang.controller.mapper.repair.RepairTicketMapper;
import com.zhongbang.controller.pojo.order.entity.OrderLine;
import com.zhongbang.controller.pojo.order.entity.SalesOrder;
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
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售毛利月报和年报。数字都在这里算，不交给大模型。
 * 口径：ALL 全部，INVOICED 只看开票，UNINVOICED 只看不开票。
 * 未知成本的订单行不进商品成本和毛利。
 */
@Service
public class MarginReportService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final SalesOrderMapper salesOrderMapper;
    private final OrderLineMapper orderLineMapper;
    private final RepairTicketMapper repairTicketMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final CostPriceService costPriceService;
    private final StockAlertService stockAlertService;

    public MarginReportService(
            SalesOrderMapper salesOrderMapper,
            OrderLineMapper orderLineMapper,
            RepairTicketMapper repairTicketMapper,
            ProductMapper productMapper,
            ProductSkuMapper productSkuMapper,
            CostPriceService costPriceService,
            StockAlertService stockAlertService
    ) {
        this.salesOrderMapper = salesOrderMapper;
        this.orderLineMapper = orderLineMapper;
        this.repairTicketMapper = repairTicketMapper;
        this.productMapper = productMapper;
        this.productSkuMapper = productSkuMapper;
        this.costPriceService = costPriceService;
        this.stockAlertService = stockAlertService;
    }

    public ReportVo.Report report(String periodType, String periodKey, String scope) {
        StaffRoles.requireBoss("只有老板可以查看销售毛利");
        Period period = period(periodType, periodKey);
        String invoiceScope = scope(scope);
        ReportVo.Totals current = totals(period, invoiceScope);
        ReportVo.Totals previous = totals(period.previous(), invoiceScope);
        ReportVo.Totals yearAgo = period.yearAgo() == null ? null : totals(period.yearAgo(), invoiceScope);
        return new ReportVo.Report(period.type(), period.key(), invoiceScope, current, previous, yearAgo);
    }

    public ReportVo.Products products(String periodType, String periodKey, String scope) {
        StaffRoles.requireBoss("只有老板可以查看销售毛利");
        Period period = period(periodType, periodKey);
        String invoiceScope = scope(scope);
        return new ReportVo.Products(period.type(), period.key(), invoiceScope, (int) period.days(), skuRows(period, invoiceScope));
    }

    public byte[] export(String periodType, String periodKey, String scope) {
        ReportVo.Report report = report(periodType, periodKey, scope);
        ReportVo.Products products = products(periodType, periodKey, scope);
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet summary = book.createSheet("汇总");
            int row = 0;
            row = writeTotals(summary, row, "本期 " + report.current().periodKey(), report.current());
            row = writeTotals(summary, row + 1, "对比 " + report.previous().periodKey(), report.previous());
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

    private ReportVo.Totals totals(Period period, String scope) {
        List<SalesOrder> orders = orders(period, scope);
        Map<Long, SalesOrder> byId = new HashMap<>();
        for (SalesOrder order : orders) {
            byId.put(order.getId(), order);
        }
        List<OrderLine> lines = linesOf(byId.keySet());
        long sales = 0;
        long salesIn = 0;
        long salesEx = 0;
        long unknownSales = 0;
        int unknownLines = 0;
        long knownNet = 0;
        long cost = 0;
        boolean anyKnown = false;
        for (OrderLine line : lines) {
            SalesOrder order = byId.get(line.getOrderId());
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
        long received = 0;
        long receivedIn = 0;
        long receivedEx = 0;
        long unpaid = 0;
        long install = 0;
        long installIn = 0;
        long installEx = 0;
        for (SalesOrder order : orders) {
            long paid = order.getPaidAmount() == null ? 0L : order.getPaidAmount();
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
        for (RepairTicket ticket : repairs(period, scope)) {
            long paid = ticket.getPaidAmount() == null ? 0L : ticket.getPaidAmount();
            TradeMath.TaxParts parts = TradeMath.split(paid, ticket.getInvoiced() != null && ticket.getInvoiced() == 1, ticket.getTaxIncluded(), ticket.getTaxRate());
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
                install, installIn, installEx, repair, repairIn, repairEx
        );
    }

    private List<ReportVo.SkuRow> skuRows(Period period, String scope) {
        List<SalesOrder> orders = orders(period, scope);
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

    private List<SalesOrder> orders(Period period, String scope) {
        List<SalesOrder> orders = salesOrderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                .ge(SalesOrder::getCreatedAt, period.start())
                .lt(SalesOrder::getCreatedAt, period.end())
                .ne(SalesOrder::getStatus, "CANCELLED"));
        return orders.stream().filter(order -> matchScope(scope, invoiced(order))).toList();
    }

    private List<RepairTicket> repairs(Period period, String scope) {
        List<RepairTicket> tickets = repairTicketMapper.selectList(new LambdaQueryWrapper<RepairTicket>()
                .ge(RepairTicket::getCreatedAt, period.start())
                .lt(RepairTicket::getCreatedAt, period.end())
                .ne(RepairTicket::getStatus, "CANCELLED"));
        return tickets.stream().filter(ticket -> matchScope(scope, ticket.getInvoiced() != null && ticket.getInvoiced() == 1)).toList();
    }

    private List<OrderLine> linesOf(java.util.Collection<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return List.of();
        }
        return orderLineMapper.selectList(new LambdaQueryWrapper<OrderLine>().in(OrderLine::getOrderId, orderIds));
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

    public static Period period(String periodType, String periodKey) {
        String type = periodType == null || periodType.isBlank() ? "MONTH" : periodType.trim().toUpperCase();
        LocalDate today = LocalDate.now(SHANGHAI);
        if ("YEAR".equals(type)) {
            int year = periodKey == null || periodKey.isBlank() ? today.getYear() : parseYear(periodKey.trim());
            LocalDateTime start = LocalDate.of(year, 1, 1).atStartOfDay();
            return new Period("YEAR", Integer.toString(year), start, start.plusYears(1),
                    new Period("YEAR", Integer.toString(year - 1), start.minusYears(1), start, null, null));
        }
        if (!"MONTH".equals(type)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "周期只能是月或年");
        }
        YearMonth month = periodKey == null || periodKey.isBlank() ? YearMonth.from(today) : parseMonth(periodKey.trim());
        LocalDateTime start = month.atDay(1).atStartOfDay();
        YearMonth prev = month.minusMonths(1);
        YearMonth ago = month.minusYears(1);
        return new Period("MONTH", month.toString(), start, start.plusMonths(1),
                new Period("MONTH", prev.toString(), prev.atDay(1).atStartOfDay(), prev.plusMonths(1).atDay(1).atStartOfDay(), null, null),
                new Period("MONTH", ago.toString(), ago.atDay(1).atStartOfDay(), ago.plusMonths(1).atDay(1).atStartOfDay(), null, null));
    }

    private static int parseYear(String key) {
        if (!key.matches("\\d{4}")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "年份请写成 2026");
        }
        return Integer.parseInt(key);
    }

    private static YearMonth parseMonth(String key) {
        if (!key.matches("\\d{4}-\\d{2}")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "月份请写成 2026-11");
        }
        return YearMonth.parse(key);
    }

    private static String yuan(long fen) {
        boolean negative = fen < 0;
        long abs = Math.abs(fen);
        return (negative ? "-" : "") + (abs / 100) + "." + String.format("%02d", abs % 100);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    public record Period(String type, String key, LocalDateTime start, LocalDateTime end, Period previous, Period yearAgo) {
        Period(String type, String key, LocalDateTime start, LocalDateTime end, Period previous) {
            this(type, key, start, end, previous, null);
        }

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
