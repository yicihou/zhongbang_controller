/**
 * 按业务领域分的包。每个领域尽量自己闭环：Controller 接请求，Service 写规则，Mapper 访问表。
 *
 * <p>已经有代码的：{@code auth}（客户登录）、{@code customer}（设备档案）、
 * {@code product}（空列表）、{@code system}（探活）。
 * {@code order}、{@code payment}、{@code workorder}、{@code aftersale}、{@code technician}、
 * {@code notice}、{@code report} 只有本说明文件，没有类。对应的表在 {@code V1__init.sql} 里已经建了。
 */
package com.zhongbang.controller.module;
