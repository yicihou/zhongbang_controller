package com.zhongbang.controller.module.notify;

import com.zhongbang.controller.module.notify.entity.AdminNotice;
import com.zhongbang.controller.module.notify.mapper.AdminNoticeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminNotifyService {

    private final AdminNoticeMapper adminNoticeMapper;
    private final AdminSseHub adminSseHub;

    public AdminNotifyService(AdminNoticeMapper adminNoticeMapper, AdminSseHub adminSseHub) {
        this.adminNoticeMapper = adminNoticeMapper;
        this.adminSseHub = adminSseHub;
    }

    @Transactional
    public void publish(String eventName, String title, String body, String bizType, Long bizId) {
        AdminNotice notice = new AdminNotice();
        notice.setTenantId(1L);
        notice.setTitle(title);
        notice.setBody(body);
        notice.setBizType(bizType);
        notice.setBizId(bizId);
        notice.setReadFlag(0);
        notice.setCreatedAt(LocalDateTime.now());
        adminNoticeMapper.insert(notice);

        Map<String, Object> payload = new HashMap<>();
        payload.put("noticeId", notice.getId());
        payload.put("title", title);
        payload.put("body", body);
        payload.put("bizType", bizType);
        payload.put("bizId", bizId);
        payload.put("createdAt", notice.getCreatedAt().toString());
        adminSseHub.broadcast(eventName, payload);
    }

    public List<AdminNotice> listUnread(int limit) {
        return adminNoticeMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AdminNotice>()
                        .eq(AdminNotice::getReadFlag, 0)
                        .orderByDesc(AdminNotice::getCreatedAt)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 100)))
        );
    }
}
