package com.zhongbang.controller.pojo.cost.vo;

import java.util.List;

/** 进价和计价设置。这些接口只给老板，别的角色进不来。 */
public final class CostVo {

    private CostVo() {
    }

    public record Setting(
            String costMethod,
            int defaultMinStock,
            int suggestMultiplier,
            int hotTurnoverDays,
            int slowTurnoverDays
    ) {
    }

    public record Log(
            Long id,
            Long skuId,
            Long beforePrice,
            Long afterPrice,
            String operatorName,
            String reason,
            String createdAt
    ) {
    }

    public record History(Long skuId, String skuName, Long costPrice, List<Log> logs) {
    }
}
