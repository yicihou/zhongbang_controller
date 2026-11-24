package com.zhongbang.controller.pojo.order.dto;

import java.util.List;

/**
 * 下单、改价、发货、派工、取消的请求体。金额单位都是分。
 * 字段名和 admin-web 的 types.ts、小程序提交的 JSON 对齐。
 */
public final class OrderRequests {

    private OrderRequests() {
    }

    /** 小程序下单。items 是购物车多行；只有一行时也可以只传 skuId。 */
    public static class Create {
        private String requestId;
        private String contactName;
        private String contactMobile;
        private String addressText;
        private Long addressId;
        private String remark;
        private Long skuId;
        private Integer quantity;
        private String deliveryMethod;
        private String installDate;
        private String installSlot;
        private Long technicianId;
        private List<Line> items;

        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        public String getContactName() { return contactName; }
        public void setContactName(String contactName) { this.contactName = contactName; }
        public String getContactMobile() { return contactMobile; }
        public void setContactMobile(String contactMobile) { this.contactMobile = contactMobile; }
        public String getAddressText() { return addressText; }
        public void setAddressText(String addressText) { this.addressText = addressText; }
        public Long getAddressId() { return addressId; }
        public void setAddressId(Long addressId) { this.addressId = addressId; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
        public Long getSkuId() { return skuId; }
        public void setSkuId(Long skuId) { this.skuId = skuId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public String getDeliveryMethod() { return deliveryMethod; }
        public void setDeliveryMethod(String deliveryMethod) { this.deliveryMethod = deliveryMethod; }
        public String getInstallDate() { return installDate; }
        public void setInstallDate(String installDate) { this.installDate = installDate; }
        public String getInstallSlot() { return installSlot; }
        public void setInstallSlot(String installSlot) { this.installSlot = installSlot; }
        public Long getTechnicianId() { return technicianId; }
        public void setTechnicianId(Long technicianId) { this.technicianId = technicianId; }
        public List<Line> getItems() { return items; }
        public void setItems(List<Line> items) { this.items = items; }
    }

    public static class Line {
        private Long skuId;
        private Integer quantity;
        private String deliveryMethod;
        private String installDate;
        private String installSlot;
        private Long technicianId;

        public Long getSkuId() { return skuId; }
        public void setSkuId(Long skuId) { this.skuId = skuId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public String getDeliveryMethod() { return deliveryMethod; }
        public void setDeliveryMethod(String deliveryMethod) { this.deliveryMethod = deliveryMethod; }
        public String getInstallDate() { return installDate; }
        public void setInstallDate(String installDate) { this.installDate = installDate; }
        public String getInstallSlot() { return installSlot; }
        public void setInstallSlot(String installSlot) { this.installSlot = installSlot; }
        public Long getTechnicianId() { return technicianId; }
        public void setTechnicianId(Long technicianId) { this.technicianId = technicianId; }
    }

    public static class AdjustPrice {
        private Long adjustAmount;
        private List<AdjustLine> lines;
        private String reason;

        public Long getAdjustAmount() { return adjustAmount; }
        public void setAdjustAmount(Long adjustAmount) { this.adjustAmount = adjustAmount; }
        public List<AdjustLine> getLines() { return lines; }
        public void setLines(List<AdjustLine> lines) { this.lines = lines; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class AdjustLine {
        private Long orderItemId;
        private Long unitPrice;
        private Long installFee;

        public Long getOrderItemId() { return orderItemId; }
        public void setOrderItemId(Long orderItemId) { this.orderItemId = orderItemId; }
        public Long getUnitPrice() { return unitPrice; }
        public void setUnitPrice(Long unitPrice) { this.unitPrice = unitPrice; }
        public Long getInstallFee() { return installFee; }
        public void setInstallFee(Long installFee) { this.installFee = installFee; }
    }

    public static class Freight {
        private Long freightAmount;

        public Long getFreightAmount() { return freightAmount; }
        public void setFreightAmount(Long freightAmount) { this.freightAmount = freightAmount; }
    }

    public static class CreatePayments {
        private String mode;
        private List<PaymentDraft> payments;

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public List<PaymentDraft> getPayments() { return payments; }
        public void setPayments(List<PaymentDraft> payments) { this.payments = payments; }
    }

    public static class PaymentDraft {
        private String type;
        private String title;
        private Long amount;
        private String channel;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public Long getAmount() { return amount; }
        public void setAmount(Long amount) { this.amount = amount; }
        public String getChannel() { return channel; }
        public void setChannel(String channel) { this.channel = channel; }
    }

    public static class PatchPayment {
        private Long amount;
        private String title;

        public Long getAmount() { return amount; }
        public void setAmount(Long amount) { this.amount = amount; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
    }

    public static class OfflineConfirm {
        private Long amount;
        private String method;
        private String paidAt;
        private List<String> voucherUrls;
        private String remark;

        public Long getAmount() { return amount; }
        public void setAmount(Long amount) { this.amount = amount; }
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        public String getPaidAt() { return paidAt; }
        public void setPaidAt(String paidAt) { this.paidAt = paidAt; }
        public List<String> getVoucherUrls() { return voucherUrls; }
        public void setVoucherUrls(List<String> voucherUrls) { this.voucherUrls = voucherUrls; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }

    public static class ShipmentCreate {
        private Long orderItemId;
        private String method;
        private String carrier;
        private String trackingNo;
        private String logisticsCompany;
        private String freightNo;
        private String vehicleNo;
        private String courierName;
        private String courierMobile;
        private String appointAt;
        private String pickupCode;
        private Long pickupStoreId;
        private String remark;

        public Long getOrderItemId() { return orderItemId; }
        public void setOrderItemId(Long orderItemId) { this.orderItemId = orderItemId; }
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        public String getCarrier() { return carrier; }
        public void setCarrier(String carrier) { this.carrier = carrier; }
        public String getTrackingNo() { return trackingNo; }
        public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }
        public String getLogisticsCompany() { return logisticsCompany; }
        public void setLogisticsCompany(String logisticsCompany) { this.logisticsCompany = logisticsCompany; }
        public String getFreightNo() { return freightNo; }
        public void setFreightNo(String freightNo) { this.freightNo = freightNo; }
        public String getVehicleNo() { return vehicleNo; }
        public void setVehicleNo(String vehicleNo) { this.vehicleNo = vehicleNo; }
        public String getCourierName() { return courierName; }
        public void setCourierName(String courierName) { this.courierName = courierName; }
        public String getCourierMobile() { return courierMobile; }
        public void setCourierMobile(String courierMobile) { this.courierMobile = courierMobile; }
        public String getAppointAt() { return appointAt; }
        public void setAppointAt(String appointAt) { this.appointAt = appointAt; }
        public String getPickupCode() { return pickupCode; }
        public void setPickupCode(String pickupCode) { this.pickupCode = pickupCode; }
        public Long getPickupStoreId() { return pickupStoreId; }
        public void setPickupStoreId(Long pickupStoreId) { this.pickupStoreId = pickupStoreId; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }

    public static class Assign {
        private Long orderItemId;
        private Long technicianId;
        private String installDate;
        private String installSlot;
        private String reason;

        public Long getOrderItemId() { return orderItemId; }
        public void setOrderItemId(Long orderItemId) { this.orderItemId = orderItemId; }
        public Long getTechnicianId() { return technicianId; }
        public void setTechnicianId(Long technicianId) { this.technicianId = technicianId; }
        public String getInstallDate() { return installDate; }
        public void setInstallDate(String installDate) { this.installDate = installDate; }
        public String getInstallSlot() { return installSlot; }
        public void setInstallSlot(String installSlot) { this.installSlot = installSlot; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class Cancel {
        private String reason;

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
