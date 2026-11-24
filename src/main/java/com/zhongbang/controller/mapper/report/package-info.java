/**
 * 报表的表访问占位，目前没有 Mapper。
 * 以后的接口放在本包并标 {@code @Mapper}，启动类的扫描范围已经包含这里。
 *
 * <p>规划中做聚合查询。现在没有类，也没有报表接口。
 * 同一业务在四层各有一个同名子包，目前都只有说明文件：
 * {@code controller.report}、{@code service.report}、{@code mapper.report}、{@code pojo.report}。
 */
package com.zhongbang.controller.mapper.report;
