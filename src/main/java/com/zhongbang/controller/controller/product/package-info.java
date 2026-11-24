/**
 * 商品 HTTP 入口，路径 {@code /api/products}，无需登录。列表固定返回空页，详情返回占位字段。
 *
 * <p>表 {@code product}、{@code product_sku} 已由 Flyway 建好。Java 侧只有这个空壳，不读这两张表，
 * 所以还没有 {@code service.product}、{@code mapper.product} 或 {@code pojo.product}。
 */
package com.zhongbang.controller.controller.product;
