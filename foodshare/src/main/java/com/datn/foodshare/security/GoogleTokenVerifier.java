package com.datn.foodshare.security;

import com.datn.foodshare.domain.response.GoogleUserInfo;
import com.datn.foodshare.util.error.BusinessException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
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
            if (idToken == null) {
                throw new BadCredentialsException("Google ID Token không hợp lệ hoặc đã hết hạn");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
                throw new BadCredentialsException("Tài khoản Google chưa xác minh email");
            }

            return GoogleUserInfo.builder()
                    .googleId(payload.getSubject())
                    .email(payload.getEmail())
                    .emailVerified(payload.getEmailVerified())
                    .name((String) payload.get("name"))
                    .picture((String) payload.get("picture"))
                    .build();
        } catch (BusinessException | BadCredentialsException ex) {
            throw ex;
        } catch (Exception e) {
            log.warn("Không thể xác minh Google ID Token: {}", e.getMessage());
            throw new BadCredentialsException("Google ID Token không hợp lệ hoặc đã hết hạn");
        }
    }
}
