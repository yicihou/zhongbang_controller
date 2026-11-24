/**
 * 安装 / 维修工单的 HTTP 入口占位，目前没有 Controller。
 * 以后的接口放在本包，只接收请求并调用同名 Service。
 *
 * <p>表 {@code work_order} 已建好。没有派单、签到或完工代码。
 * 同一业务在四层各有一个同名子包，目前都只有说明文件：
 * {@code controller.workorder}、{@code service.workorder}、{@code mapper.workorder}、{@code pojo.workorder}。
 */
package com.zhongbang.controller.controller.workorder;
