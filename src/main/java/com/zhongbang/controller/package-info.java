/**
 * 后端启动类所在的根包。
 *
 * <p>Spring Boot 启动时从这里往下扫描子包，把标了 {@code @Component}、{@code @Service}、
 * {@code @RestController} 的类登记成可以互相注入的对象。
 *
 * <p>一次 HTTP 请求的大致路径：小程序或浏览器 → {@code TraceIdFilter}（记下跟踪号）
 * → Spring Security 过滤器链（看 JWT）→ {@code controller} 层某个业务子包里的 Controller
 * → 同名的 {@code service} 子包 → 同名的 {@code mapper} 子包 → 数据库，
 * 再包成 <code>{code,msg,data,traceId}</code> 返回。
 * 请求体、表对应的对象和返回字段放在 {@code pojo} 的同名业务子包，它们自己不访问数据库。
 *
 * <p>顶层按层分包，层内再按业务建子包。{@code security}、{@code config}、{@code common}、
 * {@code adapter}、{@code support}、{@code job} 不按业务拆。
 * 当前代码是骨架：登录、客户设备档案、商品空列表和健康检查能跑通。
 * 订单、支付、报修等业务在四层下都只有说明文件，表结构在 Flyway 脚本里，Java 还没写对应的类。
 */
package com.zhongbang.controller;
