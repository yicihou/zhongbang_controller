/**
 * 设备档案的 HTTP 入口，路径 {@code /api/assets}。要求 JWT 角色是客户（{@code ROLE_CUSTOMER}）。
 *
 * <p>已实现：当前登录客户查询自己的设备、按序列号绑定一台占位档案。规则在 {@code service.customer}。
 * 客户地址、标签、跟进记录还没有接口。客户行本身是登录时在 {@code service.auth} 里写入的。
 */
package com.zhongbang.controller.controller.customer;
