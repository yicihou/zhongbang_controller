package com.zhongbang.controller.controller.notice;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.pojo.notice.vo.NoticeVo;
import com.zhongbang.controller.service.notice.AdminSseHub;
import com.zhongbang.controller.service.notice.NoticeService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 管理端通知。新订单、新报修会推到 {@code /api/admin/stream}。
 * 连接失败时，页面改为轮询 {@code /api/admin/notices?unread=1}，读的是同一张表。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminNoticeController {

    private final NoticeService noticeService;
    private final AdminSseHub adminSseHub;

    public AdminNoticeController(NoticeService noticeService, AdminSseHub adminSseHub) {
        this.noticeService = noticeService;
        this.adminSseHub = adminSseHub;
    }

    @GetMapping("/notices")
    public ApiResponse<PageResult<NoticeVo>> notices(
            @RequestParam(required = false) String unread,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        boolean onlyUnread = "1".equals(unread) || "true".equalsIgnoreCase(unread);
        return ApiResponse.ok(noticeService.list(onlyUnread, page, size));
    }

    /** 浏览器带着 Authorization 头来接事件。事件名是 ORDER_CREATED 或 REPAIR_CREATED，data 是一条通知 JSON。 */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return adminSseHub.connect();
    }
}
