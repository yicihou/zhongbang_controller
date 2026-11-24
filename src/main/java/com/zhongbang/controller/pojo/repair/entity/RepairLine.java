package com.zhongbang.controller.pojo.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 报修单里的一行，对应一台设备。
 * {@code imagesJson} 存的是照片地址的 JSON 数组，例如 {@code ["https://img/a.jpg"]}。
 * {@code source} 为 INTERNAL 表示本店档案，EXTERNAL 表示客户填写的外购设备。
 */
@TableName("repair_line")
public class RepairLine {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long ticketId;
    private Integer lineNo;
    private Long assetId;
    private String brand;
    private String model;
    private String sn;
    private String source;
    private String faultType;
    private String faultDesc;
    private String imagesJson;
    private String purchaseProofUrl;
    private String purchaseChannel;
    private Long lineQuoteAmount;
    private Integer inWarranty;
    private Long workOrderId;
    private Long technicianId;
    private String technicianName;
    private String appointAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getSn() { return sn; }
    public void setSn(String sn) { this.sn = sn; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getFaultType() { return faultType; }
    public void setFaultType(String faultType) { this.faultType = faultType; }
    public String getFaultDesc() { return faultDesc; }
    public void setFaultDesc(String faultDesc) { this.faultDesc = faultDesc; }
    public String getImagesJson() { return imagesJson; }
    public void setImagesJson(String imagesJson) { this.imagesJson = imagesJson; }
    public String getPurchaseProofUrl() { return purchaseProofUrl; }
    public void setPurchaseProofUrl(String purchaseProofUrl) { this.purchaseProofUrl = purchaseProofUrl; }
    public String getPurchaseChannel() { return purchaseChannel; }
    public void setPurchaseChannel(String purchaseChannel) { this.purchaseChannel = purchaseChannel; }
    public Long getLineQuoteAmount() { return lineQuoteAmount; }
    public void setLineQuoteAmount(Long lineQuoteAmount) { this.lineQuoteAmount = lineQuoteAmount; }
    public Integer getInWarranty() { return inWarranty; }
    public void setInWarranty(Integer inWarranty) { this.inWarranty = inWarranty; }
    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }
    public Long getTechnicianId() { return technicianId; }
    public void setTechnicianId(Long technicianId) { this.technicianId = technicianId; }
    public String getTechnicianName() { return technicianName; }
    public void setTechnicianName(String technicianName) { this.technicianName = technicianName; }
    public String getAppointAt() { return appointAt; }
    public void setAppointAt(String appointAt) { this.appointAt = appointAt; }
}
