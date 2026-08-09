package com.datn.foodshare.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.util.List;

@Slf4j
@Configuration
public class FirebaseConfiguration {

    @Value("${firebase.credentials.path:}")
    private String credentialsPath;

    @Bean
    public FirebaseApp firebaseApp() {
        List<FirebaseApp> firebaseApps = FirebaseApp.getApps();
        if (firebaseApps != null && !firebaseApps.isEmpty()) {
            return firebaseApps.get(0);
        }

        if (credentialsPath == null || credentialsPath.isBlank()) {
            log.warn("Đường dẫn file chứng thực Firebase (firebase.credentials.path) chưa được cấu hình. Firebase Messaging sẽ tạm thời không khả dụng.");
            return null;
        }

        try {
            Resource resource;
            if (credentialsPath.startsWith("classpath:")) {
                resource = new ClassPathResource(credentialsPath.substring(10));
            } else {
                resource = new FileSystemResource(credentialsPath);
            }

            if (!resource.exists()) {
                log.warn("Không tìm thấy file chứng thực Firebase tại: {}. Bỏ qua khởi tạo FirebaseApp.", credentialsPath);
                return null;
            }

            try (InputStream is = resource.getInputStream()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(is))
                        .build();
                FirebaseApp app = FirebaseApp.initializeApp(options);
                log.info(">>> Khởi tạo FirebaseApp thành công.");
                return app;
            }
        } catch (Exception e) {
            log.error("Lỗi khi khởi tạo FirebaseApp: {}", e.getMessage());
            return null;
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(ObjectProvider<FirebaseApp> firebaseAppProvider) {
        FirebaseApp firebaseApp = firebaseAppProvider.getIfAvailable();
        if (firebaseApp == null) {
            log.warn("FirebaseApp chưa được khởi tạo. Bean FirebaseMessaging trả về null.");
            return null;
        }
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}
