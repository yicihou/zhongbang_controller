/**
 * 报表的数据对象占位，目前没有实体、请求体或返回体。
 * 以后按用途再分 {@code entity}（对应表）、{@code dto}（请求）、{@code vo}（返回给页面的字段）。
 * 这些类不写 SQL。
 *
 * <p>规划中做聚合查询。现在没有类，也没有报表接口。
 * 同一业务在四层各有一个同名子包，目前都只有说明文件：
 * {@code controller.report}、{@code service.report}、{@code mapper.report}、{@code pojo.report}。
 */
package com.zhongbang.controller.pojo.report;
