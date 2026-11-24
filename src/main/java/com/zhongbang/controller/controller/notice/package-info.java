/**
 * 管理端通知的 HTTP 入口。
 * {@code GET /api/admin/stream} 是 SSE，新订单会推一条。
 * {@code GET /api/admin/notices} 给断线之后的轮询用，读的是同一张 admin_notice 表。
 */
package com.zhongbang.controller.controller.notice;
