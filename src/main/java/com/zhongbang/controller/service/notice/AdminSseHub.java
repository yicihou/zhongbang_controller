package com.zhongbang.controller.service.notice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhongbang.controller.pojo.notice.vo.NoticeVo;
import com.zhongbang.controller.security.StaffRoles;
import com.zhongbang.controller.security.UserContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 管理端浏览器保持的 SSE 连接。新订单写完数据库之后，把同一份通知推给当前在线的人。
 * 没有人在听也不算失败，通知已经落在 admin_notice 表里，页面可以改轮询。
 */
@Component
public class AdminSseHub {

    private final List<Subscription> emitters = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper;

    public AdminSseHub(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 一条连接默认 30 分钟。浏览器断开或超时后从列表里拿掉，避免一直往关闭的连接写。 */
    public SseEmitter connect() {
        SseEmitter emitter = new SseEmitter(30L * 60L * 1000L);
        // 记下这条连接是不是老板。协议还款的事件只写给老板的连接。
        Subscription subscription = new Subscription(emitter, StaffRoles.isBoss(UserContext.currentOrNull()));
        emitters.add(subscription);
        emitter.onCompletion(() -> emitters.remove(subscription));
        emitter.onTimeout(() -> emitters.remove(subscription));
        emitter.onError(ex -> emitters.remove(subscription));
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (Exception ex) {
            emitters.remove(subscription);
        }
        return emitter;
    }

    public void publish(NoticeVo notice) {
        String json;
        try {
            json = objectMapper.writeValueAsString(notice);
        } catch (Exception ex) {
            return;
        }
        boolean bossOnly = notice != null && "BOSS".equals(notice.audience());
        for (Subscription subscription : emitters) {
            if (bossOnly && !subscription.boss()) {
                continue;
            }
            try {
                subscription.emitter().send(SseEmitter.event().name(notice.eventType()).data(json));
            } catch (Exception ex) {
                emitters.remove(subscription);
            }
        }
    }

    private record Subscription(SseEmitter emitter, boolean boss) {
    }
}
