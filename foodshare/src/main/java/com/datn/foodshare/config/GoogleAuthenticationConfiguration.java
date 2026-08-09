package com.datn.foodshare.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

@Slf4j
@Configuration
public class GoogleAuthenticationConfiguration {

    @Value("${app.google.client-id:}")
    private String googleClientId;

    @Bean
    public GoogleIdTokenVerifier googleIdTokenVerifier() {
        if (googleClientId == null || googleClientId.isBlank()) {
            log.warn("Google Client ID (app.google.client-id) chưa được cấu hình. Google ID Token verification sẽ không khả dụng cho đến khi được cung cấp.");
            return null;
        }

        log.info(">>> Khởi tạo GoogleIdTokenVerifier thành công với Client ID: {}", googleClientId);
        return new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(googleClientId))
                .build();
    }
}
