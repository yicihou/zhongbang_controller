package com.zhongbang.controller.controller.customer;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.pojo.customer.vo.ChangeVo;
import com.zhongbang.controller.service.customer.ChangeQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序轮询用的变更检查。要客户 JWT。
 * 订单详情、报修详情、订单列表大约每 10 秒问一次；没有变化就不必再拉整页数据。
 */
@RestController
@RequestMapping("/api/me")
public class MeChangeController {

    private final ChangeQueryService changeQueryService;

    public MeChangeController(ChangeQueryService changeQueryService) {
        this.changeQueryService = changeQueryService;
    }

    @GetMapping("/changes")
    public ApiResponse<ChangeVo> changes(@RequestParam(required = false) String since) {
        return ApiResponse.ok(changeQueryService.since(since));
    }
}
