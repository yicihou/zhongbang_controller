/**
 * 后端启动类所在的根包。
 *
 * <p>Spring Boot 启动时从这里往下扫描子包，把标了 {@code @Component}、{@code @Service}、
 * {@code @RestController} 的类登记成可以互相注入的对象。
 *
 * <p>一次 HTTP 请求的大致路径：小程序或浏览器 → {@code TraceIdFilter}（记下跟踪号）
 * → Spring Security 过滤器链（看 JWT）→ 某个 Controller → Service → Mapper → 数据库，
 * 再包成 <code>{code,msg,data,traceId}</code> 返回。
 *
 * <p>当前代码是骨架：登录、客户设备档案、商品空列表和健康检查能跑通。
 * 订单、支付、报修等业务包多数还是空目录，表结构在 Flyway 脚本里，Java 还没写对应的类。
 */
package com.zhongbang.controller;
