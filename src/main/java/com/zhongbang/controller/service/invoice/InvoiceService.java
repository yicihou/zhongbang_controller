package com.zhongbang.controller.service.invoice;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.mapper.order.SalesOrderMapper;
import com.zhongbang.controller.mapper.repair.RepairTicketMapper;
import com.zhongbang.controller.pojo.invoice.dto.InvoiceRequest;
import com.zhongbang.controller.pojo.order.entity.SalesOrder;
import com.zhongbang.controller.pojo.repair.entity.RepairTicket;
import com.zhongbang.controller.security.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 给订单和报修单标记开不开发票。税率可以空着。
 * 这不是进价，管理员也能改。报表再按开票、不开票、全部三个口径分开算。
 */
@Service
public class InvoiceService {

    private final SalesOrderMapper salesOrderMapper;
    private final RepairTicketMapper repairTicketMapper;

    public InvoiceService(SalesOrderMapper salesOrderMapper, RepairTicketMapper repairTicketMapper) {
        this.salesOrderMapper = salesOrderMapper;
        this.repairTicketMapper = repairTicketMapper;
    }

    @Transactional
    public void markOrder(long id, InvoiceRequest request) {
        UserContext.require();
        SalesOrder order = salesOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        Checked checked = check(request);
        salesOrderMapper.update(null, new LambdaUpdateWrapper<SalesOrder>()
                .eq(SalesOrder::getId, id)
                .set(SalesOrder::getInvoiced, checked.on ? 1 : 0)
                .set(SalesOrder::getInvoiceType, checked.type)
                .set(SalesOrder::getTaxRate, checked.rate)
                .set(SalesOrder::getTaxIncluded, checked.included)
                .set(SalesOrder::getUpdatedAt, DateTimes.now()));
    }

    @Transactional
    public void markRepair(long id, InvoiceRequest request) {
        UserContext.require();
        RepairTicket ticket = repairTicketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报修单不存在");
        }
        Checked checked = check(request);
        repairTicketMapper.update(null, new LambdaUpdateWrapper<RepairTicket>()
                .eq(RepairTicket::getId, id)
                .set(RepairTicket::getInvoiced, checked.on ? 1 : 0)
                .set(RepairTicket::getInvoiceType, checked.type)
                .set(RepairTicket::getTaxRate, checked.rate)
                .set(RepairTicket::getTaxIncluded, checked.included)
                .set(RepairTicket::getUpdatedAt, DateTimes.now()));
    }

    private Checked check(InvoiceRequest request) {
        boolean on = request != null && Boolean.TRUE.equals(request.getInvoiced());
        Integer rate = request == null ? null : request.getTaxRate();
        if (rate != null && (rate < 0 || rate > 10000)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "税率请按百分数乘 100 填写，例如 13% 填 1300");
        }
        Integer included = null;
        if (on && request.getTaxIncluded() != null) {
            included = request.getTaxIncluded() ? 1 : 0;
        }
        String type = on && request.getInvoiceType() != null && !request.getInvoiceType().isBlank()
                ? request.getInvoiceType().trim() : null;
        return new Checked(on, type, on ? rate : null, included);
    }

    private record Checked(boolean on, String type, Integer rate, Integer included) {
    }
}
