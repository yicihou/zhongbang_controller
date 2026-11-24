package com.zhongbang.controller.service.order;

import com.zhongbang.controller.pojo.order.entity.OrderLine;
import com.zhongbang.controller.pojo.order.entity.PriceLog;
import com.zhongbang.controller.pojo.order.entity.SalesOrder;
import com.zhongbang.controller.pojo.order.entity.Shipment;
import com.zhongbang.controller.pojo.payment.entity.PaymentBill;

import java.util.ArrayList;
import java.util.List;

/**
 * 内存里的一张完整订单：头、行、应付、改价记录。
 * 规则先改这个对象，通过后再由 {@code OrderService} 写回数据库。
 * {@code freshPayments} 里的是这次新拆出来、还没插入的应付。
 */
public class OrderBundle {

    private SalesOrder order;
    private List<OrderLine> items = new ArrayList<>();
    private List<PaymentBill> payments = new ArrayList<>();
    private List<PriceLog> priceLogs = new ArrayList<>();
    private final List<PaymentBill> freshPayments = new ArrayList<>();
    private final List<PriceLog> freshLogs = new ArrayList<>();
    private final List<Shipment> freshShipments = new ArrayList<>();

    public SalesOrder getOrder() { return order; }
    public void setOrder(SalesOrder order) { this.order = order; }
    public List<OrderLine> getItems() { return items; }
    public void setItems(List<OrderLine> items) { this.items = items; }
    public List<PaymentBill> getPayments() { return payments; }
    public void setPayments(List<PaymentBill> payments) { this.payments = payments; }
    public List<PriceLog> getPriceLogs() { return priceLogs; }
    public void setPriceLogs(List<PriceLog> priceLogs) { this.priceLogs = priceLogs; }
    public List<PaymentBill> getFreshPayments() { return freshPayments; }
    public List<PriceLog> getFreshLogs() { return freshLogs; }
    public List<Shipment> getFreshShipments() { return freshShipments; }

    public void addPayment(PaymentBill bill) {
        payments.add(bill);
        freshPayments.add(bill);
    }

    public void addLog(PriceLog log) {
        priceLogs.add(0, log);
        freshLogs.add(log);
    }
}
