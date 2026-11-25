package com.zhongbang.controller.controller.cost;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.pojo.cost.vo.CostVo;
import com.zhongbang.controller.service.cost.CostPriceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 进价和计价方式。类上要求老板角色，管理员和业务员调用会得到 403。
 * 进价单位是分。改一次就留一条历史。
 */
@RestController
@RequestMapping("/api/admin/costs")
@PreAuthorize("hasRole('BOSS')")
public class AdminCostController {

    private final CostPriceService costPriceService;

    public AdminCostController(CostPriceService costPriceService) {
        this.costPriceService = costPriceService;
    }

    @GetMapping("/settings")
    public ApiResponse<CostVo.Setting> settings() {
        return ApiResponse.ok(costPriceService.readSetting());
    }

    @PutMapping("/settings")
    public ApiResponse<CostVo.Setting> updateSettings(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(costPriceService.updateSetting(
                text(body.get("costMethod")),
                integer(body.get("defaultMinStock")),
                integer(body.get("suggestMultiplier")),
                integer(body.get("hotTurnoverDays")),
                integer(body.get("slowTurnoverDays"))
        ));
    }

    @PutMapping("/skus/{skuId}")
    public ApiResponse<CostVo.History> setCost(@PathVariable long skuId, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(costPriceService.setCost(skuId, longValue(body.get("costPrice")), text(body.get("reason"))));
    }

    @GetMapping("/skus/{skuId}/logs")
    public ApiResponse<CostVo.History> logs(@PathVariable long skuId) {
        return ApiResponse.ok(costPriceService.history(skuId));
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer integer(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private static Long longValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Long.valueOf(String.valueOf(value));
    }
}
