package com.datn.foodshare.controller;

import com.datn.foodshare.domain.request.RegisterDeviceRequest;
import com.datn.foodshare.service.DeviceService;
import com.datn.foodshare.util.annotation.ApiMessage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @PostMapping
    @ApiMessage("Đăng ký thiết bị thành công")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterDeviceRequest request) {
        deviceService.register(request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/current")
    @ApiMessage("Hủy đăng ký thiết bị thành công")
    public ResponseEntity<Void> deactivateCurrentUserDevices() {
        deviceService.deactivateCurrentUserDevices();
        return ResponseEntity.noContent().build();
    }
}
