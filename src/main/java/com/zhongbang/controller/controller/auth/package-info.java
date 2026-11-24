/**
 * 客户小程序登录的 HTTP 入口。只做参数接收和把结果包进 {@code ApiResponse}，
 * 换 openid、建客户、签发令牌在 {@code service.auth}。
 *
 * <p>已实现：{@code POST /api/auth/login} 用 code 换本地 openid，没有客户就插入一行，签发
 * {@code type=CUSTOMER} 的 JWT。
 * 未实现：{@code POST /api/auth/phone} 会直接返回「尚未实现」。
 * 管理端用的 {@code POST /api/auth/staff/login} 后端没有这个接口。
 */
package com.zhongbang.controller.controller.auth;
