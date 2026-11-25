package com.zhongbang.controller.pojo.purchase.dto;

import java.util.List;

/** 供应商、采购单和付款的请求体。金额单位是分。业务员提交的进价会被忽略。 */
public final class PurchaseRequests {

    private PurchaseRequests() {
    }

    public static class SupplierSave {
        private String name;
        private String contact;
        private String mobile;
        private String brands;
        private Integer accountDays;
        private String remark;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getContact() { return contact; }
        public void setContact(String contact) { this.contact = contact; }
        public String getMobile() { return mobile; }
        public void setMobile(String mobile) { this.mobile = mobile; }
        public String getBrands() { return brands; }
        public void setBrands(String brands) { this.brands = brands; }
        public Integer getAccountDays() { return accountDays; }
        public void setAccountDays(Integer accountDays) { this.accountDays = accountDays; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }

    public static class PurchaseSave {
        private Long supplierId;
        private String orderDate;
        private Long freightAmount;
        private Boolean invoiced;
        private String invoiceType;
        private Integer taxRate;
        private Boolean taxIncluded;
        private List<String> attachmentUrls;
        private String remark;
        private List<Line> lines;

        public Long getSupplierId() { return supplierId; }
        public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
        public String getOrderDate() { return orderDate; }
        public void setOrderDate(String orderDate) { this.orderDate = orderDate; }
        public Long getFreightAmount() { return freightAmount; }
        public void setFreightAmount(Long freightAmount) { this.freightAmount = freightAmount; }
        public Boolean getInvoiced() { return invoiced; }
        public void setInvoiced(Boolean invoiced) { this.invoiced = invoiced; }
        public String getInvoiceType() { return invoiceType; }
        public void setInvoiceType(String invoiceType) { this.invoiceType = invoiceType; }
        public Integer getTaxRate() { return taxRate; }
        public void setTaxRate(Integer taxRate) { this.taxRate = taxRate; }
        public Boolean getTaxIncluded() { return taxIncluded; }
        public void setTaxIncluded(Boolean taxIncluded) { this.taxIncluded = taxIncluded; }
        public List<String> getAttachmentUrls() { return attachmentUrls; }
        public void setAttachmentUrls(List<String> attachmentUrls) { this.attachmentUrls = attachmentUrls; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
        public List<Line> getLines() { return lines; }
        public void setLines(List<Line> lines) { this.lines = lines; }
    }

    public static class Line {
        private Long skuId;
        private Integer quantity;
        private Long unitCost;

        public Long getSkuId() { return skuId; }
        public void setSkuId(Long skuId) { this.skuId = skuId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public Long getUnitCost() { return unitCost; }
        public void setUnitCost(Long unitCost) { this.unitCost = unitCost; }
    }

    public static class Payment {
        private Long amount;
        private String method;
        private String remark;

        public Long getAmount() { return amount; }
        public void setAmount(Long amount) { this.amount = amount; }
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }
}
