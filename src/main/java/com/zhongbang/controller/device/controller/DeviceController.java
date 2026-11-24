package com.zhongbang.controller.device.controller;

import com.zhongbang.controller.common.api.ApiResponse;
import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.device.dto.DeviceCreateRequest;
import com.zhongbang.controller.device.dto.DeviceResponse;
import com.zhongbang.controller.device.dto.DeviceUpdateRequest;
import com.zhongbang.controller.device.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping
    public ApiResponse<PageResult<DeviceResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(deviceService.list(page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<DeviceResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(deviceService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DeviceResponse> create(@Valid @RequestBody DeviceCreateRequest request) {
        return ApiResponse.ok(deviceService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<DeviceResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody DeviceUpdateRequest request
    ) {
        return ApiResponse.ok(deviceService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        deviceService.delete(id);
    }
}
