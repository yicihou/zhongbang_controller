package com.zhongbang.controller.module.customer.dto;

import jakarta.validation.constraints.NotBlank;

/** 绑定设备的请求体。序列号会在服务里 trim，唯一索引保证全表不能有两条相同 sn。 */
public record AssetBindRequest(
        @NotBlank String sn
) {
}
