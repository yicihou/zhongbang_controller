/**
 * 报表的业务规则占位，目前没有 Service。
 * 以后的规则放在本包，由同名 Controller 调用，通过同名 Mapper 落库。
 * 需要拆成接口和实现时，实现类放在本包下的 {@code impl}。
 *
 * <p>规划中做聚合查询。现在没有类，也没有报表接口。
 * 同一业务在四层各有一个同名子包，目前都只有说明文件：
 * {@code controller.report}、{@code service.report}、{@code mapper.report}、{@code pojo.report}。
 */
package com.zhongbang.controller.service.report;
