package com.datn.foodshare.controller.admin;

import com.datn.foodshare.domain.request.UpdateSystemConfigRequest;
import com.datn.foodshare.domain.response.SystemConfigResponse;
import com.datn.foodshare.service.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminSystemConfigControllerTest {

    @Mock
    private SystemConfigService systemConfigService;

    private AdminSystemConfigController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminSystemConfigController(systemConfigService);
    }

    @Test
    void getAllConfigs_returns200() {
        List<SystemConfigResponse> list = List.of(SystemConfigResponse.builder().configKey("KEY").configValue("VAL").build());
        when(systemConfigService.getAllAdminConfigs()).thenReturn(list);

        ResponseEntity<List<SystemConfigResponse>> result = controller.getAllConfigs();

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(list, result.getBody());
    }

    @Test
    void updateConfig_returns200() {
        UpdateSystemConfigRequest request = new UpdateSystemConfigRequest();
        SystemConfigResponse response = SystemConfigResponse.builder().configKey("KEY").configValue("NEW_VAL").build();
        when(systemConfigService.updateConfig("KEY", request)).thenReturn(response);

        ResponseEntity<SystemConfigResponse> result = controller.updateConfig("KEY", request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());
    }
}
