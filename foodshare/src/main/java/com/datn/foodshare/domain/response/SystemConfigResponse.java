package com.datn.foodshare.domain.response;

import com.datn.foodshare.domain.entity.SystemConfig;
import com.datn.foodshare.util.constant.ConfigDataType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SystemConfigResponse {
    private String configKey;
    private String configValue;
    private String description;
    private ConfigDataType dataType;

    public static SystemConfigResponse from(SystemConfig config) {
        return SystemConfigResponse.builder()
                .configKey(config.getConfigKey())
                .configValue(config.getConfigValue())
                .description(config.getDescription())
                .dataType(config.getDataType())
                .build();
    }
}
