/**
 * 安装 / 维修工单的业务规则占位，目前没有 Service。
 * 以后的规则放在本包，由同名 Controller 调用，通过同名 Mapper 落库。
 * 需要拆成接口和实现时，实现类放在本包下的 {@code impl}。
 *
 * <p>表 {@code work_order} 已建好。没有派单、签到或完工代码。
 * 同一业务在四层各有一个同名子包，目前都只有说明文件：
 * {@code controller.workorder}、{@code service.workorder}、{@code mapper.workorder}、{@code pojo.workorder}。
 */
package com.zhongbang.controller.service.workorder;
