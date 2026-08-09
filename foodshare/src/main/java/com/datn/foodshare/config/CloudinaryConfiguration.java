package com.datn.foodshare.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class CloudinaryConfiguration {

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

    @Value("${cloudinary.api-secret:}")
    private String apiSecret;

    @Bean
    public Cloudinary cloudinary() {
        if (cloudName == null || cloudName.isBlank()) {
            log.warn("Cloudinary cloud-name chưa được cấu hình. Sử dụng cấu hình mặc định/trống.");
        }

        Map<String, Object> config = new HashMap<>();
        config.put("cloud_name", cloudName != null ? cloudName : "");
        config.put("api_key", apiKey != null ? apiKey : "");
        config.put("api_secret", apiSecret != null ? apiSecret : "");
        config.put("secure", true);

        return new Cloudinary(config);
    }
}
