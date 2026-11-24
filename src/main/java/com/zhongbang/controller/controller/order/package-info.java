/**
 * 订单的 HTTP 入口占位，目前没有 Controller。
 * 以后的接口放在本包，只接收请求并调用同名 Service。
 *
 * <p>表 {@code orders} 已建好（旧的单头模型：总额、定金、尾款、整单安装时间）。
 * 管理端订单页的改价、发货、派工都在 {@code admin-web} 的 Mock 里，请求不会进入这些包。
 * 同一业务在四层各有一个同名子包，目前都只有说明文件：
 * {@code controller.order}、{@code service.order}、{@code mapper.order}、{@code pojo.order}。
 */
package com.zhongbang.controller.controller.order;
