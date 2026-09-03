package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.SystemConfig;
import com.datn.foodshare.domain.request.UpdateSystemConfigRequest;
import com.datn.foodshare.domain.response.SystemConfigResponse;
import com.datn.foodshare.repository.SystemConfigRepository;
import com.datn.foodshare.util.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;

    @Transactional(readOnly = true)
    public List<SystemConfigResponse> getAllAdminConfigs() {
        return systemConfigRepository.findAll()
                .stream()
                .map(SystemConfigResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public SystemConfigResponse updateConfig(String configKey, UpdateSystemConfigRequest request) {
        SystemConfig config = systemConfigRepository.findByConfigKey(configKey)
                .orElseThrow(() -> new BusinessException("Không tìm thấy cấu hình với mã: " + configKey));

        // TODO: Validate configValue based on config.getDataType()
        config.setConfigValue(request.getConfigValue());
        
        return SystemConfigResponse.from(systemConfigRepository.save(config));
    }
}
