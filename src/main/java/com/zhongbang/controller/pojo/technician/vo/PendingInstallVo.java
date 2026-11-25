package com.zhongbang.controller.pojo.technician.vo;

/**
 * 还没排师傅的上门安装行。排期页点师傅名字或格子时，在这个列表里挑一单派进去。
 * orderItemId 是派工接口要的 id；orderId 用来拼 {@code /api/admin/orders/{id}/assign}。
 */
public record PendingInstallVo(
        Long orderItemId,
        Long orderId,
        String orderNo,
        String customerName,
        String productName,
        String skuName,
        int quantity,
        String addressText
) {
}
