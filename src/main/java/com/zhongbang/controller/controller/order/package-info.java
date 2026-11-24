/**
 * 订单的 HTTP 入口。
 * {@code OrderController} 给小程序：多行下单、我的订单、未发货时取消。
 * {@code AdminOrderController} 给管理端：列表、详情、改价、运费、发货、派师傅、取消。
 * 金额和状态的判断不写在这里，而在 {@code service.order}。
 */
package com.zhongbang.controller.controller.order;
