/**
 * 管理端收款的 HTTP 入口。
 * 线下登记和待收款列表在 {@code AdminPaymentController}。
 * 部分收款时，差额会拆成标题带「（余款）」的新待付，规则在订单服务里。
 */
package com.zhongbang.controller.controller.payment;
