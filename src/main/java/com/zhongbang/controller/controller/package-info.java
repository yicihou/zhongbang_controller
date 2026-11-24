/**
 * HTTP 入口层。浏览器或小程序打过来的请求先到这里。
 *
 * <p>本层按业务再分子包，例如 {@code auth}、{@code customer}。
 * 一个 Controller 只做三件事：接收路径和 JSON、调用同名业务的 Service、把结果放进统一响应。
 * 能不能登录、设备归谁，这类规则不写在这里。
 *
 * <p>已经有类的业务：{@code auth}（客户登录）、{@code customer}（设备档案）、
 * {@code product}（空列表）、{@code system}（探活）。
 * {@code order}、{@code payment}、{@code workorder}、{@code aftersale}、{@code technician}、
 * {@code notice}、{@code report} 只有说明文件，没有 Controller。
 */
package com.zhongbang.controller.controller;
