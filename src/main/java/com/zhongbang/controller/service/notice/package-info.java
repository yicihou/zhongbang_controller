/**
 * 通知的业务规则占位，目前没有 Service。
 * 以后的规则放在本包，由同名 Controller 调用，通过同名 Mapper 落库。
 * 需要拆成接口和实现时，实现类放在本包下的 {@code impl}。
 *
 * <p>规划中有订阅消息和发送记录。现在没有类。
 * 管理端顶栏的实时通知是前端 Mock，或将来的 {@code /api/admin/stream}，后端还没实现。
 * 同一业务在四层各有一个同名子包，目前都只有说明文件：
 * {@code controller.notice}、{@code service.notice}、{@code mapper.notice}、{@code pojo.notice}。
 */
package com.zhongbang.controller.service.notice;
