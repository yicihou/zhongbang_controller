/**
 * 售后 / 报修的表访问占位，目前没有 Mapper。
 * 以后的接口放在本包并标 {@code @Mapper}，启动类的扫描范围已经包含这里。
 *
 * <p>规划中有报修单、保修判定、报价。现在四层都没有类。
 * 管理端报修页面走的是前端 Mock，不会进入这些包。
 * 同一业务在四层各有一个同名子包，目前都只有说明文件：
 * {@code controller.aftersale}、{@code service.aftersale}、{@code mapper.aftersale}、{@code pojo.aftersale}。
 */
package com.zhongbang.controller.mapper.aftersale;
