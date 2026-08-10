package com.datn.foodshare.security;

import com.datn.foodshare.domain.response.GoogleUserInfo;
import com.datn.foodshare.util.error.BusinessException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GoogleTokenVerifier {

    private final GoogleIdTokenVerifier googleIdTokenVerifier;

    public GoogleTokenVerifier(@Autowired(required = false) GoogleIdTokenVerifier googleIdTokenVerifier) {
        this.googleIdTokenVerifier = googleIdTokenVerifier;
    }

    public GoogleUserInfo verify(String idTokenString) {
        if (googleIdTokenVerifier == null) {
            throw new BusinessException("Google Authentication chưa được cấu hình Client ID trên hệ thống");
        }

        try {
            GoogleIdToken idToken = googleIdTokenVerifier.verify(idTokenString);
            if (idToken != null) {
                GoogleIdToken.Payload payload = idToken.getPayload();

                return GoogleUserInfo.builder()
                        .googleId(payload.getSubject())
                        .email(payload.getEmail())
                        .emailVerified(payload.getEmailVerified())
                        .name((String) payload.get("name"))
                        .picture((String) payload.get("picture"))
                        .build();
            } else {
                throw new BusinessException("Google ID Token không hợp lệ hoặc đã hết hạn");
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("Lỗi khi xác minh Google ID Token: {}", e.getMessage());
            throw new BusinessException("Không thể xác minh tài khoản Google: " + e.getMessage());
        }
    }
}
