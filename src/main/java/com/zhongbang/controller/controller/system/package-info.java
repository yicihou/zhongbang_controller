/**
 * {@code GET /api/system/ping}：确认进程活着，并带回服务名和时间。公开接口，不查数据库。
 *
 * <p>这不是员工、角色、操作日志那套「系统管理」，那些还没写，因此没有对应的 Service 或 Mapper。
 */
package com.zhongbang.controller.controller.system;
