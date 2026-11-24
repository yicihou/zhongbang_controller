/**
 * 师傅档案的数据对象占位，目前没有实体、请求体或返回体。
 * 以后按用途再分 {@code entity}（对应表）、{@code dto}（请求）、{@code vo}（返回给页面的字段）。
 * 这些类不写 SQL。
 *
 * <p>表 {@code technician} 已建好。没有查询或排班接口。
 * 安全配置预留了 {@code /api/tech/**}，只允许 {@code ROLE_TECHNICIAN}，还没有人签发这种 JWT。
 * 同一业务在四层各有一个同名子包，目前都只有说明文件：
 * {@code controller.technician}、{@code service.technician}、{@code mapper.technician}、{@code pojo.technician}。
 */
package com.zhongbang.controller.pojo.technician;
