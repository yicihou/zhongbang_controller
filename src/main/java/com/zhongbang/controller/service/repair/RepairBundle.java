package com.zhongbang.controller.service.repair;

import com.zhongbang.controller.pojo.payment.entity.PaymentBill;
import com.zhongbang.controller.pojo.repair.entity.RepairLine;
import com.zhongbang.controller.pojo.repair.entity.RepairTicket;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次操作里用到的报修单、行和收款。
 * {@code freshPayments} 是这次新造出来、还没插入数据库的应付单。保存时只 insert 这些，其余 update。
 */
public class RepairBundle {

    private RepairTicket ticket;
    private List<RepairLine> lines = new ArrayList<>();
    private List<PaymentBill> payments = new ArrayList<>();
    private final List<PaymentBill> freshPayments = new ArrayList<>();

    public RepairTicket getTicket() { return ticket; }
    public void setTicket(RepairTicket ticket) { this.ticket = ticket; }
    public List<RepairLine> getLines() { return lines; }
    public void setLines(List<RepairLine> lines) { this.lines = lines; }
    public List<PaymentBill> getPayments() { return payments; }
    public void setPayments(List<PaymentBill> payments) { this.payments = payments; }
    public List<PaymentBill> getFreshPayments() { return freshPayments; }

    public void addPayment(PaymentBill bill) {
        payments.add(bill);
        freshPayments.add(bill);
    }
}
