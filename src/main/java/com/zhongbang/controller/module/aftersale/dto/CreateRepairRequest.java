package com.zhongbang.controller.module.aftersale.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.List;

public record CreateRepairRequest(
        LocalDate expectDate,
        String expectSlot,
        String contactMobile,
        String remark,
        @NotEmpty @Valid List<Line> lines
) {
    public record Line(
            Long assetId,
            ExternalDevice external,
            @NotBlank String faultType,
            @NotBlank String faultDesc,
            @NotEmpty List<String> images,
            String purchaseProofUrl,
            String purchaseChannel
    ) {
    }

    public record ExternalDevice(
            String brand,
            String model,
            String sn
    ) {
    }
}
