package com.zhongbang.controller.pojo.repair.dto;

import java.util.List;

/**
 * 报修请求体。金额单位是分。字段名对齐小程序 {@code pages/repair/create.js}
 * 和管理端 {@code RepairQuoteRequest}、{@code RepairAssignRequest}。
 */
public final class RepairRequests {

    private RepairRequests() {
    }

    /** 小程序提交。一行要么带本店 {@code assetId}，要么带 {@code external}。 */
    public static class Create {
        private String expectDate;
        private String expectSlot;
        private String remark;
        private String contactMobile;
        private List<Line> lines;

        public String getExpectDate() { return expectDate; }
        public void setExpectDate(String expectDate) { this.expectDate = expectDate; }
        public String getExpectSlot() { return expectSlot; }
        public void setExpectSlot(String expectSlot) { this.expectSlot = expectSlot; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
        public String getContactMobile() { return contactMobile; }
        public void setContactMobile(String contactMobile) { this.contactMobile = contactMobile; }
        public List<Line> getLines() { return lines; }
        public void setLines(List<Line> lines) { this.lines = lines; }
    }

    public static class Line {
        private Long assetId;
        private String faultType;
        private String faultDesc;
        private List<String> images;
        private External external;

        public Long getAssetId() { return assetId; }
        public void setAssetId(Long assetId) { this.assetId = assetId; }
        public String getFaultType() { return faultType; }
        public void setFaultType(String faultType) { this.faultType = faultType; }
        public String getFaultDesc() { return faultDesc; }
        public void setFaultDesc(String faultDesc) { this.faultDesc = faultDesc; }
        public List<String> getImages() { return images; }
        public void setImages(List<String> images) { this.images = images; }
        public External getExternal() { return external; }
        public void setExternal(External external) { this.external = external; }
    }

    /** 外购设备。照片可以放在本对象的 images，也可以放在外层行的 images。 */
    public static class External {
        private String brand;
        private String model;
        private String sn;
        private String faultDesc;
        private List<String> images;
        private String purchaseChannel;
        private String purchaseProof;
        private String purchaseProofUrl;

        public String getBrand() { return brand; }
        public void setBrand(String brand) { this.brand = brand; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getSn() { return sn; }
        public void setSn(String sn) { this.sn = sn; }
        public String getFaultDesc() { return faultDesc; }
        public void setFaultDesc(String faultDesc) { this.faultDesc = faultDesc; }
        public List<String> getImages() { return images; }
        public void setImages(List<String> images) { this.images = images; }
        public String getPurchaseChannel() { return purchaseChannel; }
        public void setPurchaseChannel(String purchaseChannel) { this.purchaseChannel = purchaseChannel; }
        public String getPurchaseProof() { return purchaseProof; }
        public void setPurchaseProof(String purchaseProof) { this.purchaseProof = purchaseProof; }
        public String getPurchaseProofUrl() { return purchaseProofUrl; }
        public void setPurchaseProofUrl(String purchaseProofUrl) { this.purchaseProofUrl = purchaseProofUrl; }
    }

    /** 管理端报价。保内或合计为 0 时不生成待付。 */
    public static class Quote {
        private Boolean inWarranty;
        private List<QuoteLine> lines;
        private String remark;

        public Boolean getInWarranty() { return inWarranty; }
        public void setInWarranty(Boolean inWarranty) { this.inWarranty = inWarranty; }
        public List<QuoteLine> getLines() { return lines; }
        public void setLines(List<QuoteLine> lines) { this.lines = lines; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }

    public static class QuoteLine {
        private Long lineId;
        private Long lineQuoteAmount;

        public Long getLineId() { return lineId; }
        public void setLineId(Long lineId) { this.lineId = lineId; }
        public Long getLineQuoteAmount() { return lineQuoteAmount; }
        public void setLineQuoteAmount(Long lineQuoteAmount) { this.lineQuoteAmount = lineQuoteAmount; }
    }

    /** 按一行派师傅。appointAt 用 yyyy-MM-dd HH:mm:ss。 */
    public static class Assign {
        private Long lineId;
        private Long technicianId;
        private String appointAt;

        public Long getLineId() { return lineId; }
        public void setLineId(Long lineId) { this.lineId = lineId; }
        public Long getTechnicianId() { return technicianId; }
        public void setTechnicianId(Long technicianId) { this.technicianId = technicianId; }
        public String getAppointAt() { return appointAt; }
        public void setAppointAt(String appointAt) { this.appointAt = appointAt; }
    }

    /** 小程序确认或拒绝报价。accept 缺省当成同意。 */
    public static class Confirm {
        private Boolean accept;
        private String remark;

        public Boolean getAccept() { return accept; }
        public void setAccept(Boolean accept) { this.accept = accept; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }
}
