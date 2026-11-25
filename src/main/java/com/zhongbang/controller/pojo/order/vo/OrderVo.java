package com.zhongbang.controller.pojo.order.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 返回给页面的订单。管理端字段对齐 admin-web 的 AdminOrder。
 * 小程序多用 totalAmount、statusText、items.price 这些短名字，和原来的 Mock 页面兼容。
 */
public final class OrderVo {

    private OrderVo() {
    }

    public record Admin(
            Long id,
            String orderNo,
            Long customerId,
            String customerName,
            String customerMobile,
            Long storeId,
            String status,
            long goodsAmount,
            long installAmount,
            long freightAmount,
            long adjustAmount,
            long payableAmount,
            long paidAmount,
            String contactRemark,
            String adminRemark,
            boolean priceAdjusted,
            String addressText,
            String createdAt,
            String updatedAt,
            List<Item> items,
            List<Payment> payments,
            List<PriceLog> priceLogs,
            boolean invoiced,
            String invoiceType,
            Integer taxRate,
            Boolean taxIncluded,
            Long goodsCost,
            Long grossProfit
    ) {
    }

    public record Item(
            Long id,
            Long orderId,
            Long skuId,
            String productName,
            String skuName,
            String coverImage,
            int quantity,
            long unitPrice,
            long lineAmount,
            String deliveryMethod,
            Long installFee,
            String installDate,
            String installSlot,
            Long technicianId,
            String technicianName,
            String addressSnapshotJson,
            Long shipmentId,
            Shipment shipment,
            String workOrderNo,
            Long costPrice,
            Long costAmount,
            Boolean costKnown
    ) {
    }

    public record Shipment(
            Long id,
            Long orderId,
            Long orderItemId,
            String method,
            String status,
            String carrier,
            String trackingNo,
            String logisticsCompany,
            String freightNo,
            String vehicleNo,
            String courierName,
            String courierMobile,
            String appointAt,
            String pickupCode,
            Long pickupStoreId,
            String pickedAt,
            String remark,
            String createdAt,
            String updatedAt
    ) {
    }

    public record Payment(
            Long id,
            String bizType,
            Long bizId,
            String paymentNo,
            String outTradeNo,
            String channel,
            String type,
            String title,
            long amount,
            String status,
            int sortNo,
            List<String> voucherUrls,
            String offlineMethod,
            String paidAt,
            Long operatorId,
            String operatorName,
            String remark,
            Long splitFromId,
            String createdAt,
            String updatedAt
    ) {
    }

    public record PriceLog(
            Long id,
            Long orderId,
            String scope,
            Long orderItemId,
            @JsonProperty("field") String field,
            Long beforeAmount,
            long afterAmount,
            String reason,
            String operatorName,
            String createdAt
    ) {
    }

    /** 小程序订单卡片和详情。orderId 在创建结果里单独给，详情用 id。 */
    public record Mine(
            Long id,
            String orderNo,
            String status,
            String statusText,
            long totalAmount,
            long payableAmount,
            long paidAmount,
            long balanceAmount,
            String createdAt,
            String installDate,
            String installSlot,
            String technicianName,
            String remark,
            Address address,
            List<MineItem> items,
            List<String> actions,
            Tech technician,
            List<Step> timeline
    ) {
    }

    public record Tech(Long id, String name, String mobile) {
    }

    public record Step(String status, String title, String desc, boolean done, boolean current) {
    }

    public record MineItem(
            Long id,
            Long skuId,
            String name,
            String skuName,
            String coverImage,
            long price,
            int quantity,
            String deliveryMethod
    ) {
    }

    public record Address(String name, String mobile, String detail) {
    }

    public record Created(
            Long orderId,
            String orderNo,
            long totalAmount,
            long payableAmount,
            long paidAmount,
            long balanceAmount,
            String status
    ) {
    }
}
