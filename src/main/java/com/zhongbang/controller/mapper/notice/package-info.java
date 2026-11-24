/**
 * 通知的表访问占位，目前没有 Mapper。
 * 以后的接口放在本包并标 {@code @Mapper}，启动类的扫描范围已经包含这里。
 *
 * <p>规划中有订阅消息和发送记录。现在没有类。
 * 管理端顶栏的实时通知是前端 Mock，或将来的 {@code /api/admin/stream}，后端还没实现。
 * 同一业务在四层各有一个同名子包，目前都只有说明文件：
 * {@code controller.notice}、{@code service.notice}、{@code mapper.notice}、{@code pojo.notice}。
 */
package com.zhongbang.controller.mapper.notice;
