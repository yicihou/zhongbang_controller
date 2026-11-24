/**
 * 安装 / 维修工单的表访问占位，目前没有 Mapper。
 * 以后的接口放在本包并标 {@code @Mapper}，启动类的扫描范围已经包含这里。
 *
 * <p>表 {@code work_order} 已建好。没有派单、签到或完工代码。
 * 同一业务在四层各有一个同名子包，目前都只有说明文件：
 * {@code controller.workorder}、{@code service.workorder}、{@code mapper.workorder}、{@code pojo.workorder}。
 */
package com.zhongbang.controller.mapper.workorder;
