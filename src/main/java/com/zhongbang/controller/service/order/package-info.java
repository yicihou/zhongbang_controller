/**
 * 订单规则和落库。
 * {@code OrderRules} 不访问数据库，负责金额、状态、发货和部分收款拆余款。
 * {@code OrderService} 查表、扣库存、占档期，再把结果写回去。
 */
package com.zhongbang.controller.service.order;
