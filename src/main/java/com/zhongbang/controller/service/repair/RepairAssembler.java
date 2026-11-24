package com.zhongbang.controller.service.repair;

import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.common.util.Jsons;
import com.zhongbang.controller.pojo.order.vo.OrderVo;
import com.zhongbang.controller.pojo.payment.entity.PaymentBill;
import com.zhongbang.controller.pojo.repair.entity.RepairLine;
import com.zhongbang.controller.pojo.repair.entity.RepairTicket;
import com.zhongbang.controller.pojo.repair.vo.RepairVo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把报修单翻译成页面要的 JSON。管理端要行、应付和师傅；小程序要状态中文和进度。
 */
public final class RepairAssembler {

    private static final Map<String, String> STATUS_TEXT = Map.of(
            "SUBMITTED", "已提交",
            "QUOTED", "已报价",
            "PENDING_PAYMENT", "待付款",
            "PAID", "已付款",
            "CONFIRMED", "待派工",
            "IN_SERVICE", "维修中",
            "DONE", "已完成",
            "CANCELLED", "已关闭"
    );

    private static final Map<String, String> FAULT_TEXT = Map.of(
            "THREAD_BREAK", "断线 / 跳针",
            "NOISE", "噪音异响",
            "OIL", "不上油",
            "ELECTRIC", "电控故障",
            "FEED", "送布异常",
            "OTHER", "其他"
    );

    private RepairAssembler() {
    }

    public static String statusText(String status) {
        return STATUS_TEXT.getOrDefault(status, status == null ? "" : status);
    }

    public static RepairVo.Admin admin(RepairBundle bundle) {
        RepairTicket ticket = bundle.getTicket();
        return new RepairVo.Admin(
                ticket.getId(),
                ticket.getTicketNo(),
                ticket.getCustomerId(),
                ticket.getCustomerName(),
                ticket.getStatus(),
                ticket.getExpectDate(),
                ticket.getExpectSlot(),
                ticket.getContactMobile(),
                ticket.getRemark(),
                ticket.getQuoteAmount(),
                nz(ticket.getPayableAmount()),
                nz(ticket.getPaidAmount()),
                warrantyFlag(ticket.getInWarranty()),
                DateTimes.format(ticket.getCreatedAt()),
                DateTimes.format(ticket.getUpdatedAt()),
                lines(bundle.getLines()),
                payments(bundle.getPayments())
        );
    }

    public static RepairVo.Created created(RepairBundle bundle) {
        RepairTicket ticket = bundle.getTicket();
        return new RepairVo.Created(
                ticket.getId(),
                ticket.getId(),
                ticket.getTicketNo(),
                ticket.getStatus(),
                statusText(ticket.getStatus()),
                bundle.getLines().size()
        );
    }

    public static RepairVo.MineItem mineItem(RepairBundle bundle) {
        RepairTicket ticket = bundle.getTicket();
        String fault = bundle.getLines().isEmpty() ? "报修" : faultText(bundle.getLines().get(0).getFaultType());
        return new RepairVo.MineItem(
                ticket.getId(),
                ticket.getTicketNo(),
                ticket.getStatus(),
                statusText(ticket.getStatus()),
                ticket.getQuoteAmount(),
                bundle.getLines().size(),
                DateTimes.format(ticket.getCreatedAt()),
                ticket.getExpectDate(),
                fault
        );
    }

    public static RepairVo.Mine mine(RepairBundle bundle) {
        RepairTicket ticket = bundle.getTicket();
        List<RepairVo.Line> rows = lines(bundle.getLines());
        boolean inWarranty = displayWarranty(ticket, bundle.getLines());
        String fault = bundle.getLines().isEmpty() ? "报修" : faultText(bundle.getLines().get(0).getFaultType());
        return new RepairVo.Mine(
                ticket.getId(),
                ticket.getTicketNo(),
                ticket.getTicketNo(),
                ticket.getStatus(),
                statusText(ticket.getStatus()),
                "维修",
                ticket.getExpectDate(),
                ticket.getExpectSlot(),
                fault,
                inWarranty,
                inWarranty ? "全部保内（待核实）" : "含保外或外购，需报价",
                ticket.getQuoteAmount(),
                nz(ticket.getPayableAmount()),
                nz(ticket.getPaidAmount()),
                DateTimes.format(ticket.getCreatedAt()),
                rows,
                quoteItems(bundle.getLines()),
                payments(bundle.getPayments()),
                timeline(ticket, bundle.getLines().size())
        );
    }

    public static OrderVo.Payment payment(PaymentBill bill) {
        return new OrderVo.Payment(
                bill.getId(),
                bill.getBizType(),
                bill.getBizId(),
                bill.getPaymentNo(),
                bill.getOutTradeNo(),
                bill.getChannel(),
                bill.getType(),
                bill.getTitle(),
                nz(bill.getAmount()),
                bill.getStatus(),
                bill.getSortNo() == null ? 0 : bill.getSortNo(),
                Jsons.readStrings(bill.getVoucherUrls()),
                bill.getOfflineMethod(),
                DateTimes.format(bill.getPaidAt()),
                bill.getOperatorId(),
                bill.getOperatorName(),
                bill.getRemark(),
                bill.getSplitFromId(),
                DateTimes.format(bill.getCreatedAt()),
                DateTimes.format(bill.getUpdatedAt())
        );
    }

    private static List<RepairVo.Line> lines(List<RepairLine> rows) {
        List<RepairVo.Line> list = new ArrayList<>();
        for (RepairLine row : rows) {
            list.add(new RepairVo.Line(
                    row.getId(),
                    row.getTicketId(),
                    row.getLineNo() == null ? 0 : row.getLineNo(),
                    row.getAssetId(),
                    row.getBrand() == null ? "" : row.getBrand(),
                    row.getModel(),
                    row.getSn(),
                    row.getSource(),
                    row.getFaultType(),
                    row.getFaultDesc(),
                    Jsons.readStrings(row.getImagesJson()),
                    row.getPurchaseProofUrl(),
                    row.getPurchaseChannel(),
                    row.getLineQuoteAmount(),
                    row.getWorkOrderId(),
                    row.getTechnicianId(),
                    row.getTechnicianName(),
                    row.getAppointAt()
            ));
        }
        return list;
    }

    private static List<OrderVo.Payment> payments(List<PaymentBill> bills) {
        List<OrderVo.Payment> list = new ArrayList<>();
        for (PaymentBill bill : bills) {
            list.add(payment(bill));
        }
        return list;
    }

    private static List<RepairVo.QuoteItem> quoteItems(List<RepairLine> rows) {
        List<RepairVo.QuoteItem> items = new ArrayList<>();
        for (RepairLine row : rows) {
            if (row.getLineQuoteAmount() == null || row.getLineQuoteAmount() <= 0) {
                continue;
            }
            String name = ((row.getBrand() == null ? "" : row.getBrand()) + " " + row.getModel()).trim();
            items.add(new RepairVo.QuoteItem(name, row.getLineQuoteAmount()));
        }
        return items;
    }

    /** 管理员还没报价时，按各行提交时的保修推断；报价之后以管理员的保内标记为准。 */
    private static boolean displayWarranty(RepairTicket ticket, List<RepairLine> rows) {
        if (ticket.getInWarranty() != null) {
            return ticket.getInWarranty() == 1;
        }
        if (rows.isEmpty()) {
            return false;
        }
        for (RepairLine row : rows) {
            if (row.getInWarranty() == null || row.getInWarranty() != 1) {
                return false;
            }
        }
        return true;
    }

    private static Boolean warrantyFlag(Integer flag) {
        if (flag == null) {
            return null;
        }
        return flag == 1;
    }

    private static List<RepairVo.Timeline> timeline(RepairTicket ticket, int lineCount) {
        String status = ticket.getStatus();
        int rank = rank(status);
        String submittedAt = DateTimes.format(ticket.getCreatedAt());
        List<RepairVo.Timeline> steps = new ArrayList<>();
        steps.add(step("SUBMITTED", "提交报修", lineCount + " 台设备", submittedAt, rank >= 0, rank == 0));
        steps.add(step("QUOTED", "报价", rank >= 2 ? "管理员已报价" : "等待报价", rank >= 2 ? DateTimes.format(ticket.getUpdatedAt()) : "", rank >= 2, rank == 1 || rank == 2));
        if ("CONFIRMED".equals(status) || (ticket.getInWarranty() != null && ticket.getInWarranty() == 1)) {
            steps.add(step("CONFIRMED", "可派工", "保内或 0 元", rank >= 3 ? DateTimes.format(ticket.getUpdatedAt()) : "", rank >= 3, "CONFIRMED".equals(status)));
        } else {
            steps.add(step("PENDING_PAYMENT", "待付维修费", "线下转账后派工", rank >= 4 ? DateTimes.format(ticket.getUpdatedAt()) : "", rank >= 4, "PENDING_PAYMENT".equals(status)));
        }
        steps.add(step("IN_SERVICE", "维修中", "已派师傅", rank >= 6 ? DateTimes.format(ticket.getUpdatedAt()) : "", rank >= 6, "IN_SERVICE".equals(status)));
        steps.add(step("DONE", "完成", "CANCELLED".equals(status) ? "已取消" : "维修结束", "DONE".equals(status) ? DateTimes.format(ticket.getUpdatedAt()) : "", rank >= 7, "DONE".equals(status) || "CANCELLED".equals(status)));
        return steps;
    }

    private static RepairVo.Timeline step(String status, String title, String desc, String time, boolean done, boolean current) {
        return new RepairVo.Timeline(status, title, desc, time == null ? "" : time, done, current);
    }

    private static int rank(String status) {
        return switch (status == null ? "" : status) {
            case "SUBMITTED" -> 0;
            case "QUOTED" -> 2;
            case "CONFIRMED" -> 3;
            case "PENDING_PAYMENT" -> 4;
            case "PAID" -> 5;
            case "IN_SERVICE" -> 6;
            case "DONE", "CANCELLED" -> 7;
            default -> 0;
        };
    }

    private static String faultText(String code) {
        if (code == null || code.isBlank()) {
            return "报修";
        }
        return FAULT_TEXT.getOrDefault(code, code);
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }
}
