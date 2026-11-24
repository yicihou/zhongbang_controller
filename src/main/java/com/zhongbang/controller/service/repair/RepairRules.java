package com.zhongbang.controller.service.repair;

import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.common.util.Jsons;
import com.zhongbang.controller.pojo.order.dto.OrderRequests;
import com.zhongbang.controller.pojo.payment.entity.PaymentBill;
import com.zhongbang.controller.pojo.repair.dto.RepairRequests;
import com.zhongbang.controller.pojo.repair.entity.RepairLine;
import com.zhongbang.controller.pojo.repair.entity.RepairTicket;
import com.zhongbang.controller.service.order.OrderRules;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 报修的状态和金额规则。这个类不查数据库。
 *
 * <p>报价：已取消、已完成、维修中不能再报。保内或合计为 0 时关掉待付，状态变成可派工（CONFIRMED）。
 * 保外则生成或更新一笔 type=REPAIR 的待付，状态变成待付款。
 * 线下收款可以只收一部分，差额变成标题带「（余款）」的新待付，算法和订单相同。
 */
public final class RepairRules {

    private RepairRules() {
    }

    /** 发新 id。由 Service 接到主键生成器上。 */
    public interface Ids {
        long next();
    }

    public static void quote(RepairBundle bundle, RepairRequests.Quote req, Ids ids) {
        RepairTicket ticket = bundle.getTicket();
        if ("CANCELLED".equals(ticket.getStatus()) || "DONE".equals(ticket.getStatus()) || "IN_SERVICE".equals(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "当前状态不允许报价");
        }
        if (req == null || req.getLines() == null || req.getLines().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写各行报价");
        }
        for (RepairRequests.QuoteLine quoted : req.getLines()) {
            RepairLine row = findLine(bundle, quoted.getLineId());
            if (quoted.getLineQuoteAmount() == null || quoted.getLineQuoteAmount() < 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "报价不能为负");
            }
            row.setLineQuoteAmount(quoted.getLineQuoteAmount());
        }
        long total = 0;
        for (RepairLine line : bundle.getLines()) {
            total += line.getLineQuoteAmount() == null ? 0 : line.getLineQuoteAmount();
        }
        boolean inWarranty = req.getInWarranty() != null && req.getInWarranty();
        ticket.setQuoteAmount(total);
        ticket.setInWarranty(inWarranty ? 1 : 0);
        if (req.getRemark() != null) {
            ticket.setRemark(req.getRemark());
        }
        ticket.setUpdatedAt(DateTimes.now());
        if (inWarranty || total == 0) {
            ticket.setPayableAmount(0L);
            LocalDateTime now = DateTimes.now();
            for (PaymentBill payment : bundle.getPayments()) {
                if ("WAIT".equals(payment.getStatus())) {
                    payment.setStatus("CLOSED");
                    payment.setUpdatedAt(now);
                }
            }
            sync(bundle);
            ticket.setStatus("CONFIRMED");
            return;
        }
        ticket.setPayableAmount(total);
        String nowText = DateTimes.nowText();
        PaymentBill waiting = null;
        for (PaymentBill payment : bundle.getPayments()) {
            if ("WAIT".equals(payment.getStatus()) && "REPAIR".equals(payment.getType())) {
                waiting = payment;
                break;
            }
        }
        if (waiting != null) {
            waiting.setAmount(total);
            waiting.setTitle("维修费");
            waiting.setUpdatedAt(DateTimes.now());
        } else {
            int sortNo = maxSort(bundle) + 1;
            bundle.addPayment(buildPayment(bundle, ids, total, sortNo, nowText));
        }
        ticket.setStatus("PENDING_PAYMENT");
        sync(bundle);
    }

    /**
     * 登记维修费到账。金额、方式、时间的校验和订单线下收款一样。
     * 金额小于这一笔时，原笔按实收改成已收，差额变成新的待付。
     */
    public static PaymentBill confirmOffline(RepairBundle bundle, long paymentId, OrderRequests.OfflineConfirm req,
                                             long operatorId, String operatorName, Ids ids) {
        RepairTicket ticket = bundle.getTicket();
        if ("CANCELLED".equals(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "已取消报修单不能登记收款");
        }
        PaymentBill payment = findPayment(bundle, paymentId);
        if (!"WAIT".equals(payment.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "该应付单不是待付状态");
        }
        OrderRequests.OfflineConfirm body = req == null ? new OrderRequests.OfflineConfirm() : req;
        String method = OrderRules.normalizeMethod(body.getMethod());
        if (method == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择收款方式");
        }
        long amount = body.getAmount() == null ? nz(payment.getAmount()) : body.getAmount();
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "本次收款金额须大于 0");
        }
        if (amount > nz(payment.getAmount())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "本次收款金额不能超过剩余 ¥" + yuan(nz(payment.getAmount())));
        }
        String now = DateTimes.nowText();
        String paidAt = body.getPaidAt() == null || body.getPaidAt().isBlank() ? now : body.getPaidAt().trim();
        if (!paidAt.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "收款时间格式应为 yyyy-MM-dd HH:mm:ss");
        }
        if (paidAt.compareTo(now) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "收款时间不能晚于当前时间");
        }
        long remaining = nz(payment.getAmount()) - amount;
        if (remaining > 0) {
            int sortNo = maxSort(bundle) + 1;
            PaymentBill rest = buildPayment(bundle, ids, remaining, sortNo, now);
            rest.setTitle(OrderRules.splitTitle(payment.getTitle()));
            rest.setType(payment.getType());
            rest.setSplitFromId(payment.getId());
            bundle.addPayment(rest);
        }
        String remark = body.getRemark() == null ? "" : body.getRemark().trim();
        payment.setAmount(amount);
        payment.setStatus("SUCCESS");
        payment.setChannel("OFFLINE");
        payment.setOfflineMethod(method);
        payment.setVoucherUrls(Jsons.writeStrings(clean(body.getVoucherUrls())));
        payment.setRemark(remark.isEmpty() ? null : remark);
        payment.setPaidAt(LocalDateTime.parse(paidAt, DateTimes.FMT));
        payment.setOperatorId(operatorId);
        payment.setOperatorName(operatorName);
        payment.setUpdatedAt(DateTimes.now());
        sync(bundle);
        return payment;
    }

    public static void cancel(RepairBundle bundle, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写取消原因");
        }
        RepairTicket ticket = bundle.getTicket();
        if ("CANCELLED".equals(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "报修单已取消");
        }
        if ("DONE".equals(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "已完成的报修单不能取消");
        }
        if ("IN_SERVICE".equals(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "已派工的报修单不能直接取消");
        }
        if (bundle.getPayments().stream().anyMatch(payment -> "SUCCESS".equals(payment.getStatus()))) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "已有成功付款，取消需走退款（二期）");
        }
        LocalDateTime now = DateTimes.now();
        for (PaymentBill payment : bundle.getPayments()) {
            if ("WAIT".equals(payment.getStatus())) {
                payment.setStatus("CLOSED");
                payment.setUpdatedAt(now);
            }
        }
        String note = "取消：" + reason.trim();
        String origin = ticket.getRemark();
        ticket.setRemark(origin == null || origin.isBlank() ? note : origin + "\n" + note);
        ticket.setStatus("CANCELLED");
        ticket.setUpdatedAt(now);
        sync(bundle);
        ticket.setStatus("CANCELLED");
    }

    /** 全部行都已派工之后，才能把整张单标成完成。 */
    public static void complete(RepairBundle bundle) {
        RepairTicket ticket = bundle.getTicket();
        if (!"IN_SERVICE".equals(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "维修中才能完工");
        }
        for (RepairLine line : bundle.getLines()) {
            if (line.getTechnicianId() == null) {
                throw new BusinessException(ErrorCode.ILLEGAL_STATE, "还有设备未派工，不能完工");
            }
        }
        ticket.setStatus("DONE");
        ticket.setUpdatedAt(DateTimes.now());
    }

    public static void assertAssignable(RepairTicket ticket) {
        String status = ticket.getStatus();
        if (!"PAID".equals(status) && !"CONFIRMED".equals(status) && !"IN_SERVICE".equals(status)) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "报价确认或付款后才能派工");
        }
    }

    /**
     * 按已成功的收款重算已付金额。
     * 已取消、已完成、维修中、可派工不再被改状态。
     * 没有待付且已付覆盖应付时，变成已付款。
     */
    public static void sync(RepairBundle bundle) {
        RepairTicket ticket = bundle.getTicket();
        long paid = 0;
        boolean waits = false;
        for (PaymentBill payment : bundle.getPayments()) {
            if ("SUCCESS".equals(payment.getStatus())) {
                paid += nz(payment.getAmount());
            }
            if ("WAIT".equals(payment.getStatus())) {
                waits = true;
            }
        }
        ticket.setPaidAmount(paid);
        String status = ticket.getStatus();
        if ("CANCELLED".equals(status) || "DONE".equals(status) || "IN_SERVICE".equals(status) || "CONFIRMED".equals(status)) {
            return;
        }
        long payable = nz(ticket.getPayableAmount());
        if (!waits && paid >= payable && payable > 0) {
            ticket.setStatus("PAID");
        }
    }

    public static PaymentBill buildPayment(RepairBundle bundle, Ids ids, long amount, int sortNo, String nowText) {
        RepairTicket ticket = bundle.getTicket();
        PaymentBill bill = new PaymentBill();
        long id = ids.next();
        String no = ticket.getTicketNo() + "-R-" + sortNo;
        bill.setId(id);
        bill.setTenantId(1L);
        bill.setOrderId(ticket.getId());
        bill.setBizType("REPAIR");
        bill.setBizId(ticket.getId());
        bill.setPaymentNo(no);
        bill.setOutTradeNo(no);
        bill.setChannel("OFFLINE");
        bill.setType("REPAIR");
        bill.setTitle("维修费");
        bill.setAmount(amount);
        bill.setStatus("WAIT");
        bill.setSortNo(sortNo);
        LocalDateTime now = LocalDateTime.parse(nowText, DateTimes.FMT);
        bill.setCreatedAt(now);
        bill.setUpdatedAt(now);
        return bill;
    }

    public static RepairLine findLine(RepairBundle bundle, Long lineId) {
        if (lineId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报修行不存在");
        }
        for (RepairLine line : bundle.getLines()) {
            if (lineId.equals(line.getId())) {
                return line;
            }
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "报修行不存在");
    }

    private static PaymentBill findPayment(RepairBundle bundle, long paymentId) {
        for (PaymentBill payment : bundle.getPayments()) {
            if (payment.getId() != null && payment.getId() == paymentId) {
                return payment;
            }
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "应付单不存在");
    }

    private static int maxSort(RepairBundle bundle) {
        int max = 0;
        for (PaymentBill payment : bundle.getPayments()) {
            if (payment.getSortNo() != null && payment.getSortNo() > max) {
                max = payment.getSortNo();
            }
        }
        return max;
    }

    private static List<String> clean(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).toList();
    }

    private static String yuan(long fen) {
        if (fen % 100 == 0) {
            return Long.toString(fen / 100);
        }
        return String.format("%.2f", fen / 100.0);
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }
}
