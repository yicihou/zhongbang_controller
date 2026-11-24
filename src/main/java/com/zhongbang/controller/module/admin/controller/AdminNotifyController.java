package com.zhongbang.controller.module.admin.controller;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.module.notify.AdminNotifyService;
import com.zhongbang.controller.module.notify.AdminSseHub;
import com.zhongbang.controller.module.notify.entity.AdminNotice;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('STAFF')")
public class AdminNotifyController {

    private final AdminSseHub adminSseHub;
    private final AdminNotifyService adminNotifyService;

    public AdminNotifyController(AdminSseHub adminSseHub, AdminNotifyService adminNotifyService) {
        this.adminSseHub = adminSseHub;
        this.adminNotifyService = adminNotifyService;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return adminSseHub.subscribe();
    }

    @GetMapping("/notices")
    public ApiResponse<List<Map<String, Object>>> notices(
            @RequestParam(defaultValue = "1") int unread,
            @RequestParam(defaultValue = "50") int size
    ) {
        List<AdminNotice> list = unread == 1
                ? adminNotifyService.listUnread(size)
                : adminNotifyService.listUnread(size);
        List<Map<String, Object>> data = list.stream().map(n -> Map.<String, Object>of(
                "id", n.getId(),
                "title", n.getTitle(),
                "body", n.getBody() == null ? "" : n.getBody(),
                "bizType", n.getBizType(),
                "bizId", n.getBizId(),
                "readFlag", n.getReadFlag(),
                "createdAt", n.getCreatedAt().toString()
        )).collect(Collectors.toList());
        return ApiResponse.ok(data);
    }

    @GetMapping("/receivables")
    public ApiResponse<List<Map<String, Object>>> receivables() {
        // TODO(P1b): list WAIT payments across orders/repairs
        return ApiResponse.ok(List.of());
    }
}
