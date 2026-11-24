/**
 * 通知的数据对象占位，目前没有实体、请求体或返回体。
 * 以后按用途再分 {@code entity}（对应表）、{@code dto}（请求）、{@code vo}（返回给页面的字段）。
 * 这些类不写 SQL。
 *
 * <p>规划中有订阅消息和发送记录。现在没有类。
 * 管理端顶栏的实时通知是前端 Mock，或将来的 {@code /api/admin/stream}，后端还没实现。
 * 同一业务在四层各有一个同名子包，目前都只有说明文件：
 * {@code controller.notice}、{@code service.notice}、{@code mapper.notice}、{@code pojo.notice}。
 */
package com.zhongbang.controller.pojo.notice;
