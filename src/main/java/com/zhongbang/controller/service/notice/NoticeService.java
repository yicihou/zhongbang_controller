package com.zhongbang.controller.service.notice;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.mapper.notice.AdminNoticeMapper;
import com.zhongbang.controller.pojo.notice.entity.AdminNotice;
import com.zhongbang.controller.pojo.notice.vo.NoticeVo;
import com.zhongbang.controller.pojo.order.entity.SalesOrder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 管理端通知。新订单先插入 admin_notice，事务提交成功后再推 SSE。
 * 这样下单如果最后回滚了，浏览器不会先看到一条并不存在的订单。
 */
@Service
public class NoticeService {

    private final AdminNoticeMapper adminNoticeMapper;
    private final AdminSseHub adminSseHub;

    public NoticeService(AdminNoticeMapper adminNoticeMapper, AdminSseHub adminSseHub) {
        this.adminNoticeMapper = adminNoticeMapper;
        this.adminSseHub = adminSseHub;
    }

    public void orderCreated(SalesOrder order) {
        AdminNotice row = new AdminNotice();
        row.setTenantId(1L);
        row.setTitle("新订单 " + order.getOrderNo());
        row.setBody(customerText(order) + " 提交了订单，应付 ¥" + yuan(order.getPayableAmount()));
        row.setBizType("ORDER");
        row.setBizId(order.getId());
        row.setEventType("ORDER_CREATED");
        row.setReadFlag(0);
        row.setCreatedAt(DateTimes.now());
        adminNoticeMapper.insert(row);
        NoticeVo vo = toVo(row);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    adminSseHub.publish(vo);
                }
            });
        } else {
            adminSseHub.publish(vo);
        }
    }

    public PageResult<NoticeVo> list(boolean unreadOnly, int page, int size) {
        int p = PageResult.normalizePage(page);
        int s = PageResult.normalizeSize(size);
        LambdaQueryWrapper<AdminNotice> wrapper = new LambdaQueryWrapper<AdminNotice>()
                .orderByDesc(AdminNotice::getCreatedAt);
        if (unreadOnly) {
            wrapper.eq(AdminNotice::getReadFlag, 0);
        }
        Page<AdminNotice> result = adminNoticeMapper.selectPage(new Page<>(p, s), wrapper);
        return PageResult.of(result.getRecords().stream().map(this::toVo).toList(), result.getTotal(), p, s);
    }

    private NoticeVo toVo(AdminNotice row) {
        return new NoticeVo(
                row.getId(),
                row.getTitle(),
                row.getBody(),
                row.getBizType(),
                row.getBizId(),
                row.getEventType(),
                row.getReadFlag() != null && row.getReadFlag() == 1,
                DateTimes.format(row.getCreatedAt())
        );
    }

    private static String customerText(SalesOrder order) {
        String name = order.getCustomerName() == null || order.getCustomerName().isBlank() ? "客户" : order.getCustomerName();
        if (order.getCustomerMobile() == null || order.getCustomerMobile().isBlank()) {
            return name;
        }
        return name + " " + order.getCustomerMobile();
    }

    private static String yuan(Long fen) {
        long value = fen == null ? 0L : fen;
        if (value % 100 == 0) {
            return Long.toString(value / 100);
        }
        return String.format("%.2f", value / 100.0);
    }
}
