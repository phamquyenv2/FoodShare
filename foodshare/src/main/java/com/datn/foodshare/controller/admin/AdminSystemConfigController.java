package com.datn.foodshare.controller.admin;

import com.datn.foodshare.domain.request.UpdateSystemConfigRequest;
import com.datn.foodshare.domain.response.SystemConfigResponse;
import com.datn.foodshare.service.SystemConfigService;
import com.datn.foodshare.util.annotation.ApiMessage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/configs")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminSystemConfigController {

    private final SystemConfigService systemConfigService;

    @GetMapping
    @ApiMessage("Lấy danh sách cấu hình hệ thống")
    public ResponseEntity<List<SystemConfigResponse>> getAllConfigs() {
        return ResponseEntity.ok(systemConfigService.getAllAdminConfigs());
    }

    @PutMapping("/{configKey}")
    @ApiMessage("Cập nhật cấu hình hệ thống thành công")
    public ResponseEntity<SystemConfigResponse> updateConfig(
            @PathVariable(name = "configKey") String configKey,
            @Valid @RequestBody UpdateSystemConfigRequest request) {
        return ResponseEntity.ok(systemConfigService.updateConfig(configKey, request));
    }
}
