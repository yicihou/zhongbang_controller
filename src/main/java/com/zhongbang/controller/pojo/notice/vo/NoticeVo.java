package com.zhongbang.controller.pojo.notice.vo;

/**
 * 管理端通知。SSE 的 data 和 {@code GET /api/admin/notices} 的列表项是同一份字段。
 */
public record NoticeVo(
        Long id,
        String title,
        String body,
        String bizType,
        Long bizId,
        String eventType,
        boolean readFlag,
        String createdAt,
        String audience
) {
}
