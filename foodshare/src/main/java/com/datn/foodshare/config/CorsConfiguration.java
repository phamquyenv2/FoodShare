package com.datn.foodshare.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfiguration {

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();

        List<String> allowedOrigins = new ArrayList<>(Arrays.asList(
                "http://localhost:3000",
                "http://localhost:5173",
                "http://localhost:4173",
                "http://localhost:8080"
        ));

        if (frontendUrl != null && !frontendUrl.isBlank()) {
            String[] urls = frontendUrl.split(",");
            for (String url : urls) {
                String trimmed = url.trim();
                if (!trimmed.isEmpty() && !allowedOrigins.contains(trimmed)) {
                    allowedOrigins.add(trimmed);
                }
            }
        }

        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "x-no-retry", "X-Requested-With"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
