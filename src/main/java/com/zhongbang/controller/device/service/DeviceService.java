package com.zhongbang.controller.device.service;

import com.zhongbang.controller.common.api.PageResult;
import com.zhongbang.controller.common.exception.BusinessException;
import com.zhongbang.controller.common.exception.ErrorCode;
import com.zhongbang.controller.device.domain.Device;
import com.zhongbang.controller.device.domain.DeviceStatus;
import com.zhongbang.controller.device.dto.DeviceCreateRequest;
import com.zhongbang.controller.device.dto.DeviceResponse;
import com.zhongbang.controller.device.dto.DeviceUpdateRequest;
import com.zhongbang.controller.device.repository.DeviceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Transactional(readOnly = true)
    public PageResult<DeviceResponse> list(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<Device> result = deviceRepository.findAll(
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id"))
        );
        return PageResult.of(
                result.getContent().stream().map(DeviceResponse::from).toList(),
                result.getTotalElements(),
                safePage,
                safeSize
        );
    }

    @Transactional(readOnly = true)
    public DeviceResponse getById(Long id) {
        return DeviceResponse.from(requireDevice(id));
    }

    @Transactional
    public DeviceResponse create(DeviceCreateRequest request) {
        if (deviceRepository.existsByCode(request.code())) {
            throw new BusinessException(ErrorCode.CONFLICT, "设备编码已存在: " + request.code());
        }
        Device device = new Device();
        device.setCode(request.code().trim());
        device.setName(request.name().trim());
        device.setDescription(trimToNull(request.description()));
        device.setStatus(DeviceStatus.OFFLINE);
        return DeviceResponse.from(deviceRepository.save(device));
    }

    @Transactional
    public DeviceResponse update(Long id, DeviceUpdateRequest request) {
        Device device = requireDevice(id);
        device.setName(request.name().trim());
        device.setStatus(request.status());
        device.setDescription(trimToNull(request.description()));
        return DeviceResponse.from(deviceRepository.save(device));
    }

    @Transactional
    public void delete(Long id) {
        if (!deviceRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "设备不存在: " + id);
        }
        deviceRepository.deleteById(id);
    }

    private Device requireDevice(Long id) {
        return deviceRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "设备不存在: " + id));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
