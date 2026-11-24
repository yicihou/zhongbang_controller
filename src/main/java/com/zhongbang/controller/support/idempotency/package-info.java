/**
 * 接口幂等占位。
 *
 * <p>设计是：下单时前端带一个 {@code requestId}，第一次记下，重复请求直接交回上次结果，避免连点出两单。
 * 实现有内存版和 Redis 版。目前没有下单接口调用 {@code begin} / {@code complete}。
 */
package com.zhongbang.controller.support.idempotency;
