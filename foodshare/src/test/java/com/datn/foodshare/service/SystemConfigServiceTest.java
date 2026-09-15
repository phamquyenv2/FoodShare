package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.SystemConfig;
import com.datn.foodshare.domain.request.UpdateSystemConfigRequest;
import com.datn.foodshare.domain.response.SystemConfigResponse;
import com.datn.foodshare.repository.SystemConfigRepository;
import com.datn.foodshare.util.constant.ConfigDataType;
import com.datn.foodshare.util.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemConfigServiceTest {

    @Mock
    private SystemConfigRepository systemConfigRepository;

    @InjectMocks
    private SystemConfigService systemConfigService;

    @Test
    void getAllAdminConfigs_mapsEveryConfigInRepositoryOrder() {
        SystemConfig fee = config("platform.fee", "0.05", ConfigDataType.NUMBER);
        SystemConfig maintenance = config("maintenance.enabled", "false", ConfigDataType.BOOLEAN);
        when(systemConfigRepository.findAll()).thenReturn(List.of(fee, maintenance));

        List<SystemConfigResponse> result = systemConfigService.getAllAdminConfigs();

        assertEquals(2, result.size());
        assertEquals("platform.fee", result.get(0).getConfigKey());
        assertEquals("0.05", result.get(0).getConfigValue());
        assertEquals(ConfigDataType.NUMBER, result.get(0).getDataType());
        assertEquals("maintenance.enabled", result.get(1).getConfigKey());
        assertEquals("false", result.get(1).getConfigValue());
        verify(systemConfigRepository).findAll();
    }

    @Test
    void updateConfig_existingKey_updatesOnlyValueAndSavesSameEntity() {
        SystemConfig existing = config("platform.fee", "0.05", ConfigDataType.NUMBER);
        existing.setDescription("Platform fee percentage");
        when(systemConfigRepository.findByConfigKey("platform.fee")).thenReturn(Optional.of(existing));
        when(systemConfigRepository.save(any(SystemConfig.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateSystemConfigRequest request = request("0.10");
        SystemConfigResponse result = systemConfigService.updateConfig("platform.fee", request);

        ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
        verify(systemConfigRepository).save(captor.capture());
        assertSame(existing, captor.getValue());
        assertEquals("0.10", captor.getValue().getConfigValue());
        assertEquals("platform.fee", captor.getValue().getConfigKey());
        assertEquals("Platform fee percentage", captor.getValue().getDescription());
        assertEquals("0.10", result.getConfigValue());
    }

    @Test
    void updateConfig_unknownKey_throwsAndDoesNotSave() {
        when(systemConfigRepository.findByConfigKey("unknown.key")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> systemConfigService.updateConfig("unknown.key", request("value"))
        );

        assertEquals("Không tìm thấy cấu hình với mã: unknown.key", exception.getMessage());
        verify(systemConfigRepository, never()).save(any());
    }

    @Test
    void updateConfig_whenRepositorySaveFails_propagatesFailure() {
        SystemConfig existing = config("platform.fee", "0.05", ConfigDataType.NUMBER);
        when(systemConfigRepository.findByConfigKey("platform.fee")).thenReturn(Optional.of(existing));
        IllegalStateException storageFailure = new IllegalStateException("database unavailable");
        when(systemConfigRepository.save(existing)).thenThrow(storageFailure);

        IllegalStateException actual = assertThrows(
                IllegalStateException.class,
                () -> systemConfigService.updateConfig("platform.fee", request("0.10"))
        );

        assertSame(storageFailure, actual);
        verify(systemConfigRepository).save(existing);
    }

    private static SystemConfig config(String key, String value, ConfigDataType dataType) {
        SystemConfig config = new SystemConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setDataType(dataType);
        return config;
    }

    private static UpdateSystemConfigRequest request(String value) {
        UpdateSystemConfigRequest request = new UpdateSystemConfigRequest();
        request.setConfigValue(value);
        return request;
    }
}
