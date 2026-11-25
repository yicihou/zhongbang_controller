package com.zhongbang.controller.service.notice;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.common.util.DateTimes;
import com.zhongbang.controller.mapper.notice.AdminNoticeMapper;
import com.zhongbang.controller.pojo.notice.entity.AdminNotice;
import com.zhongbang.controller.pojo.notice.vo.NoticeVo;
import com.zhongbang.controller.pojo.order.entity.SalesOrder;
import com.zhongbang.controller.pojo.repair.entity.RepairTicket;
import com.zhongbang.controller.security.StaffRoles;
import com.zhongbang.controller.security.UserContext;
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
        row.setAudience("ALL");
        row.setCreatedAt(DateTimes.now());
        adminNoticeMapper.insert(row);
        publishAfterCommit(toVo(row));
    }

    private void publishAfterCommit(NoticeVo vo) {
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

    /** 新报修。和订单一样，先落库，事务提交成功后再推 SSE，事件名是 REPAIR_CREATED。 */
    public void repairCreated(RepairTicket ticket, int lineCount) {
        AdminNotice row = new AdminNotice();
        row.setTenantId(1L);
        row.setTitle("新报修 " + ticket.getTicketNo());
        row.setBody(customerText(ticket.getCustomerName(), ticket.getContactMobile()) + " 提交了报修，" + lineCount + " 台设备");
        row.setBizType("REPAIR");
        row.setBizId(ticket.getId());
        row.setEventType("REPAIR_CREATED");
        row.setReadFlag(0);
        row.setAudience("ALL");
        row.setCreatedAt(DateTimes.now());
        adminNoticeMapper.insert(row);
        publishAfterCommit(toVo(row));
    }

    public PageResult<NoticeVo> list(boolean unreadOnly, int page, int size) {
        int p = PageResult.normalizePage(page);
        int s = PageResult.normalizeSize(size);
        LambdaQueryWrapper<AdminNotice> wrapper = new LambdaQueryWrapper<AdminNotice>()
                .orderByDesc(AdminNotice::getCreatedAt);
        if (unreadOnly) {
            wrapper.eq(AdminNotice::getReadFlag, 0);
        }
        // 协议还款只写给老板。其他员工轮询未读时看不到这些行，SSE 也不会推给他们。
        if (!StaffRoles.isBoss(UserContext.currentOrNull())) {
            wrapper.and(query -> query.isNull(AdminNotice::getAudience).or().ne(AdminNotice::getAudience, "BOSS"));
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
                DateTimes.format(row.getCreatedAt()),
                row.getAudience() == null || row.getAudience().isBlank() ? "ALL" : row.getAudience()
        );
    }

    /** 只推给老板的通知。协议还款提醒用这个，订单和报修仍走上面的方法。 */
    public void bossNotice(String title, String body, String bizType, Long bizId, String eventType) {
        AdminNotice row = new AdminNotice();
        row.setTenantId(1L);
        row.setTitle(title);
        row.setBody(body);
        row.setBizType(bizType);
        row.setBizId(bizId == null ? 0L : bizId);
        row.setEventType(eventType);
        row.setReadFlag(0);
        row.setAudience("BOSS");
        row.setCreatedAt(DateTimes.now());
        adminNoticeMapper.insert(row);
        publishAfterCommit(toVo(row));
    }

    private static String customerText(SalesOrder order) {
        return customerText(order.getCustomerName(), order.getCustomerMobile());
    }

    private static String customerText(String customerName, String mobile) {
        String name = customerName == null || customerName.isBlank() ? "客户" : customerName;
        if (mobile == null || mobile.isBlank()) {
            return name;
        }
        return name + " " + mobile;
    }

    private static String yuan(Long fen) {
        long value = fen == null ? 0L : fen;
        if (value % 100 == 0) {
            return Long.toString(value / 100);
        }
        return String.format("%.2f", value / 100.0);
    }
}
