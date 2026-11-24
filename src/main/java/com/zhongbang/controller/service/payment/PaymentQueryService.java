package com.zhongbang.controller.service.payment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.mapper.order.SalesOrderMapper;
import com.zhongbang.controller.mapper.payment.PaymentBillMapper;
import com.zhongbang.controller.pojo.order.entity.SalesOrder;
import com.zhongbang.controller.pojo.payment.entity.PaymentBill;
import com.zhongbang.controller.pojo.payment.vo.ReceivableVo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 待收款。把状态还是 WAIT 的应付单摊开，带上订单号和客户，给管理端「待收款」页。
 */
@Service
public class PaymentQueryService {

    private final PaymentBillMapper paymentBillMapper;
    private final SalesOrderMapper salesOrderMapper;

    public PaymentQueryService(PaymentBillMapper paymentBillMapper, SalesOrderMapper salesOrderMapper) {
        this.paymentBillMapper = paymentBillMapper;
        this.salesOrderMapper = salesOrderMapper;
    }

    public PageResult<ReceivableVo> receivables(int page, int size) {
        int p = PageResult.normalizePage(page);
        int s = PageResult.normalizeSize(size);
        List<PaymentBill> waits = paymentBillMapper.selectList(new LambdaQueryWrapper<PaymentBill>()
                .eq(PaymentBill::getStatus, "WAIT")
                .eq(PaymentBill::getBizType, "ORDER")
                .orderByDesc(PaymentBill::getCreatedAt));
        List<ReceivableVo> rows = new ArrayList<>();
        for (PaymentBill bill : waits) {
            SalesOrder order = bill.getBizId() == null ? null : salesOrderMapper.selectById(bill.getBizId());
            if (order == null || "CANCELLED".equals(order.getStatus())) {
                continue;
            }
            rows.add(new ReceivableVo(
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
            ));
        }
        rows.sort(Comparator.comparing(ReceivableVo::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
        int from = Math.min((p - 1) * s, rows.size());
        int to = Math.min(from + s, rows.size());
        return PageResult.of(rows.subList(from, to), rows.size(), p, s);
    }
}
