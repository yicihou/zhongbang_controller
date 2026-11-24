package com.zhongbang.controller.service.notice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhongbang.controller.pojo.notice.vo.NoticeVo;
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

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper;

    public AdminSseHub(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 一条连接默认 30 分钟。浏览器断开或超时后从列表里拿掉，避免一直往关闭的连接写。 */
    public SseEmitter connect() {
        SseEmitter emitter = new SseEmitter(30L * 60L * 1000L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(ex -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (Exception ex) {
            emitters.remove(emitter);
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
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(notice.eventType()).data(json));
            } catch (Exception ex) {
                emitters.remove(emitter);
            }
        }
    }
}
