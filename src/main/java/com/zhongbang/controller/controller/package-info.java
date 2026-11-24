/**
 * HTTP 入口层。浏览器或小程序打过来的请求先到这里。
 *
 * <p>本层按业务再分子包，例如 {@code auth}、{@code customer}。
 * 一个 Controller 只做三件事：接收路径和 JSON、调用同名业务的 Service、把结果放进统一响应。
 * 能不能登录、设备归谁，这类规则不写在这里。
 *
 * <p>已经有类的业务包括登录、设备、商品、订单、报修、收款、通知、师傅和探活。
 * {@code aftersale}、{@code report} 仍只有说明文件。报修在 {@code repair}，不在 {@code aftersale}。
 */
package com.zhongbang.controller.controller;
