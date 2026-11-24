package com.zhongbang.controller.controller.repair;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.pojo.repair.dto.RepairRequests;
import com.zhongbang.controller.pojo.repair.vo.RepairVo;
import com.zhongbang.controller.service.repair.RepairService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序报修。要客户登录后的 JWT。一次可以报多台，外购设备会同时写入设备档案。
 * 管理端的报价和派工在 {@code AdminRepairController}。
 */
@RestController
@RequestMapping("/api/repairs")
public class RepairController {

    private final RepairService repairService;

    public RepairController(RepairService repairService) {
        this.repairService = repairService;
    }

    @PostMapping
    public ApiResponse<RepairVo.Created> create(@RequestBody RepairRequests.Create request) {
        return ApiResponse.ok(repairService.create(request));
    }

    @GetMapping
    public ApiResponse<PageResult<RepairVo.MineItem>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(repairService.myList(status, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<RepairVo.Mine> detail(@PathVariable Long id) {
        return ApiResponse.ok(repairService.myDetail(id));
    }

    /** 保外报价后，客户可以同意或拒绝。拒绝会取消报修单。 */
    @PostMapping("/{id}/quote/confirm")
    public ApiResponse<RepairVo.Status> confirm(@PathVariable Long id, @RequestBody(required = false) RepairRequests.Confirm request) {
        return ApiResponse.ok(repairService.confirmQuote(id, request));
    }
}
