/**
 * 售后 / 报修的 HTTP 入口占位，目前没有 Controller。
 * 以后的接口放在本包，只接收请求并调用同名 Service。
 *
 * <p>规划中有报修单、保修判定、报价。现在四层都没有类。
 * 管理端报修页面走的是前端 Mock，不会进入这些包。
 * 同一业务在四层各有一个同名子包，目前都只有说明文件：
 * {@code controller.aftersale}、{@code service.aftersale}、{@code mapper.aftersale}、{@code pojo.aftersale}。
 */
package com.zhongbang.controller.controller.aftersale;
