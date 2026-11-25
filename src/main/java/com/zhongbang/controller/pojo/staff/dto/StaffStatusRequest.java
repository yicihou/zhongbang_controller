package com.zhongbang.controller.pojo.staff.dto;

/** 启用或停用。status 只能是 ENABLED 或 DISABLED。 */
public class StaffStatusRequest {

    private String status;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
