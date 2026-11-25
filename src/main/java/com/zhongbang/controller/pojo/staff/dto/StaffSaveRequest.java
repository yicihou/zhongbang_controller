package com.zhongbang.controller.pojo.staff.dto;

import java.util.List;

/**
 * 新增或编辑员工。密码只在创建登录账号、或这次要改密码时才传。
 * 角色：ADMIN 管理员，SALES 业务员，TECHNICIAN 师傅。
 */
public class StaffSaveRequest {

    private String name;
    private String mobile;
    private String role;
    private String status;
    private String remark;
    private String username;
    private String password;
    private List<String> skills;
    private String region;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
}
