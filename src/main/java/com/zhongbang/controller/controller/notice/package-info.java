/**
 * 通知的 HTTP 入口占位，目前没有 Controller。
 * 以后的接口放在本包，只接收请求并调用同名 Service。
 *
 * <p>规划中有订阅消息和发送记录。现在没有类。
 * 管理端顶栏的实时通知是前端 Mock，或将来的 {@code /api/admin/stream}，后端还没实现。
 * 同一业务在四层各有一个同名子包，目前都只有说明文件：
 * {@code controller.notice}、{@code service.notice}、{@code mapper.notice}、{@code pojo.notice}。
 */
package com.zhongbang.controller.controller.notice;
