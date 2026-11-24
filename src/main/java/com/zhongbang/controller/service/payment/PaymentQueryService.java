package com.zhongbang.controller.service.payment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.mapper.order.SalesOrderMapper;
import com.zhongbang.controller.mapper.payment.PaymentBillMapper;
import com.zhongbang.controller.mapper.repair.RepairTicketMapper;
import com.zhongbang.controller.pojo.order.entity.SalesOrder;
import com.zhongbang.controller.pojo.payment.entity.PaymentBill;
import com.zhongbang.controller.pojo.payment.vo.ReceivableVo;
import com.zhongbang.controller.pojo.repair.entity.RepairTicket;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 待收款。把状态还是 WAIT 的应付单摊开，带上订单号或报修单号和客户，给管理端「待收款」页。
 * 订单和报修共用这一张 payment 表，用 biz_type 区分。
 */
@Service
public class PaymentQueryService {

    private final PaymentBillMapper paymentBillMapper;
    private final SalesOrderMapper salesOrderMapper;
    private final RepairTicketMapper repairTicketMapper;

    public PaymentQueryService(PaymentBillMapper paymentBillMapper,
                               SalesOrderMapper salesOrderMapper,
                               RepairTicketMapper repairTicketMapper) {
        this.paymentBillMapper = paymentBillMapper;
        this.salesOrderMapper = salesOrderMapper;
        this.repairTicketMapper = repairTicketMapper;
    }

    public PageResult<ReceivableVo> receivables(int page, int size) {
        int p = PageResult.normalizePage(page);
        int s = PageResult.normalizeSize(size);
        List<PaymentBill> waits = paymentBillMapper.selectList(new LambdaQueryWrapper<PaymentBill>()
                .eq(PaymentBill::getStatus, "WAIT")
                .in(PaymentBill::getBizType, "ORDER", "REPAIR")
                .orderByDesc(PaymentBill::getCreatedAt));
        List<ReceivableVo> rows = new ArrayList<>();
        for (PaymentBill bill : waits) {
            ReceivableVo row = "REPAIR".equals(bill.getBizType()) ? repairRow(bill) : orderRow(bill);
            if (row != null) {
                rows.add(row);
            }
        }
        rows.sort(Comparator.comparing(ReceivableVo::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
        int from = Math.min((p - 1) * s, rows.size());
        int to = Math.min(from + s, rows.size());
        return PageResult.of(rows.subList(from, to), rows.size(), p, s);
    }

    private ReceivableVo orderRow(PaymentBill bill) {
        SalesOrder order = bill.getBizId() == null ? null : salesOrderMapper.selectById(bill.getBizId());
        if (order == null || "CANCELLED".equals(order.getStatus())) {
            return null;
        }
        return new ReceivableVo(
                "ORDER",
                order.getId(),
                order.getOrderNo(),
                order.getCustomerName(),
                order.getCustomerMobile(),
                bill.getId(),
                bill.getTitle(),
                bill.getType(),
                bill.getAmount() == null ? 0L : bill.getAmount(),
                bill.getStatus(),
                DateTimes.format(bill.getCreatedAt())
        );
    }

    private ReceivableVo repairRow(PaymentBill bill) {
        RepairTicket ticket = bill.getBizId() == null ? null : repairTicketMapper.selectById(bill.getBizId());
        if (ticket == null || "CANCELLED".equals(ticket.getStatus())) {
            return null;
        }
        return new ReceivableVo(
                "REPAIR",
                ticket.getId(),
                ticket.getTicketNo(),
                ticket.getCustomerName(),
                ticket.getContactMobile(),
                bill.getId(),
                bill.getTitle(),
                bill.getType(),
                bill.getAmount() == null ? 0L : bill.getAmount(),
                bill.getStatus(),
                DateTimes.format(bill.getCreatedAt())
        );
    }
}
