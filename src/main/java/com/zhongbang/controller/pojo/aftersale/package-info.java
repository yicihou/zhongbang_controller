/**
 * 售后 / 报修的数据对象占位，目前没有实体、请求体或返回体。
 * 以后按用途再分 {@code entity}（对应表）、{@code dto}（请求）、{@code vo}（返回给页面的字段）。
 * 这些类不写 SQL。
 *
 * <p>规划中有报修单、保修判定、报价。现在四层都没有类。
 * 管理端报修页面走的是前端 Mock，不会进入这些包。
 * 同一业务在四层各有一个同名子包，目前都只有说明文件：
 * {@code controller.aftersale}、{@code service.aftersale}、{@code mapper.aftersale}、{@code pojo.aftersale}。
 */
package com.zhongbang.controller.pojo.aftersale;
