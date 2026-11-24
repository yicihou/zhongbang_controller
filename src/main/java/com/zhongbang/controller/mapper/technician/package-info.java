/**
 * 师傅档案的表访问占位，目前没有 Mapper。
 * 以后的接口放在本包并标 {@code @Mapper}，启动类的扫描范围已经包含这里。
 *
 * <p>表 {@code technician} 已建好。没有查询或排班接口。
 * 安全配置预留了 {@code /api/tech/**}，只允许 {@code ROLE_TECHNICIAN}，还没有人签发这种 JWT。
 * 同一业务在四层各有一个同名子包，目前都只有说明文件：
 * {@code controller.technician}、{@code service.technician}、{@code mapper.technician}、{@code pojo.technician}。
 */
package com.zhongbang.controller.mapper.technician;
