/**
 * 各业务模块共用的返回体、错误码和工具。
 *
 * <p>这里没有订单、客户这类业务规则。Controller 抛出的可预期错误，会在
 * {@code common.exception} 里收成同一种 JSON。
 */
package com.zhongbang.controller.common;
