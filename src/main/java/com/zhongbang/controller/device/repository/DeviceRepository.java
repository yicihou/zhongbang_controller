package com.zhongbang.controller.device.repository;

import com.zhongbang.controller.device.domain.Device;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByCode(String code);

    boolean existsByCode(String code);
}
