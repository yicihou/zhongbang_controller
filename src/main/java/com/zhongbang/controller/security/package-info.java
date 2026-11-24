/**
 * 登录态：用 JWT 识别「是谁、是哪种人」，不在服务器上存 Session。
 *
 * <p>三种人：{@code CUSTOMER} 客户、{@code STAFF} 员工、{@code TECHNICIAN} 师傅。
 * 当前登录接口只会签发客户令牌。员工令牌管理端 Mock 自己签，后端验签时认 {@code type} 字段。
 *
 * <p>过滤器链在 {@code SecurityConfig}：先解析 {@code Authorization: Bearer}，
 * 再按路径决定匿名放行、要求登录，或要求某个角色。
 */
package com.zhongbang.controller.security;
