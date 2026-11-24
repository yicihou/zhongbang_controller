package com.zhongbang.controller.module.aftersale.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhongbang.controller.common.enums.AssetSource;

import java.time.LocalDateTime;

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
    private AssetSource source;
    private String faultType;
    private String faultDesc;
    private String imagesJson;
    private String purchaseProofUrl;
    private String purchaseChannel;
    private Long lineQuoteAmount;
    private Long workOrderId;
    private LocalDateTime createdAt;

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
    public AssetSource getSource() { return source; }
    public void setSource(AssetSource source) { this.source = source; }
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
    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
