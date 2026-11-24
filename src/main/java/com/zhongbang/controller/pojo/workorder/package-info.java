/**
 * 安装 / 维修工单的数据对象占位，目前没有实体、请求体或返回体。
 * 以后按用途再分 {@code entity}（对应表）、{@code dto}（请求）、{@code vo}（返回给页面的字段）。
 * 这些类不写 SQL。
 *
 * <p>表 {@code work_order} 已建好。没有派单、签到或完工代码。
 * 同一业务在四层各有一个同名子包，目前都只有说明文件：
 * {@code controller.workorder}、{@code service.workorder}、{@code mapper.workorder}、{@code pojo.workorder}。
 */
package com.zhongbang.controller.pojo.workorder;
