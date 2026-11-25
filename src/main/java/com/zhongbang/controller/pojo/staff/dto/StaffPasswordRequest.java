package com.zhongbang.controller.pojo.staff.dto;

/** 老板重置密码。不需要旧密码，新密码至少 6 位，入库前会做 BCrypt。 */
public class StaffPasswordRequest {

    private String password;

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
