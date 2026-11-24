/**
 * 售后 / 报修的业务规则占位，目前没有 Service。
 * 以后的规则放在本包，由同名 Controller 调用，通过同名 Mapper 落库。
 * 需要拆成接口和实现时，实现类放在本包下的 {@code impl}。
 *
 * <p>规划中有报修单、保修判定、报价。现在四层都没有类。
 * 管理端报修页面走的是前端 Mock，不会进入这些包。
 * 同一业务在四层各有一个同名子包，目前都只有说明文件：
 * {@code controller.aftersale}、{@code service.aftersale}、{@code mapper.aftersale}、{@code pojo.aftersale}。
 */
package com.zhongbang.controller.service.aftersale;
