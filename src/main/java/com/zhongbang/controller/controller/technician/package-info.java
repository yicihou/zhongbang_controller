/**
 * 师傅档案的 HTTP 入口占位，目前没有 Controller。
 * 以后的接口放在本包，只接收请求并调用同名 Service。
 *
 * <p>表 {@code technician} 已建好。没有查询或排班接口。
 * 安全配置预留了 {@code /api/tech/**}，只允许 {@code ROLE_TECHNICIAN}，还没有人签发这种 JWT。
 * 同一业务在四层各有一个同名子包，目前都只有说明文件：
 * {@code controller.technician}、{@code service.technician}、{@code mapper.technician}、{@code pojo.technician}。
 */
package com.zhongbang.controller.controller.technician;
