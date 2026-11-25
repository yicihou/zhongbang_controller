package com.zhongbang.controller.controller.demo;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.pojo.importdata.vo.ImportVo;
import com.zhongbang.controller.service.demo.TradeDemoService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开发环境生成进销存演示数据。生产和测试以外的环境如果关掉了开关，会直接拒绝。
 * 只有老板能点。和客户模块的「生成演示数据」是分开的按钮。
 */
@RestController
@RequestMapping("/api/admin/trade/demo")
@PreAuthorize("hasRole('BOSS')")
public class AdminTradeDemoController {

    private final TradeDemoService tradeDemoService;

    public AdminTradeDemoController(TradeDemoService tradeDemoService) {
        this.tradeDemoService = tradeDemoService;
    }

    @PostMapping
    public ApiResponse<ImportVo.DemoResult> seed() {
        return ApiResponse.ok(tradeDemoService.seed());
    }

    @DeleteMapping
    public ApiResponse<ImportVo.DemoResult> clear() {
        return ApiResponse.ok(tradeDemoService.clear());
    }
}
