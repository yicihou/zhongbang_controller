package com.zhongbang.controller.pojo.payment.vo;

/** 待收款列表的一行：还没结清的应付，带上客户姓名方便管理端核对。 */
public record ReceivableVo(
        String bizType,
        Long bizId,
        String bizNo,
        String customerName,
        String mobile,
        Long paymentId,
        String title,
        String type,
        long amount,
        String status,
        String createdAt
) {
}
