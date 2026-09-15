package com.datn.foodshare.controller;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.datn.foodshare.domain.request.GoogleLoginRequest;
import com.datn.foodshare.domain.request.LoginRequest;
import com.datn.foodshare.domain.request.RegisterRequest;
import com.datn.foodshare.domain.request.SendPhoneOtpRequest;
import com.datn.foodshare.domain.request.VerifyPhoneOtpRequest;
import com.datn.foodshare.domain.response.AuthResponse;
import com.datn.foodshare.domain.response.PhoneOtpChallengeResponse;
import com.datn.foodshare.domain.response.PhoneVerificationResponse;
import com.datn.foodshare.service.AuthService;
import com.datn.foodshare.service.PhoneOtpService;
import com.datn.foodshare.util.annotation.ApiMessage;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private final AuthService authService;
    private final PhoneOtpService phoneOtpService;

    @Value("${app.jwt.refresh-token-expiration-in-seconds:2592000}")
    private long refreshTokenExpirationInSeconds;

    @Value("${app.auth.refresh-cookie-secure:false}")
    private boolean secureCookie;

    @Value("${app.auth.refresh-cookie-same-site:Lax}")
    private String sameSite;

    @PostMapping("/register")
    @ApiMessage("Đăng ký tài khoản thành công")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        AuthService.AuthenticationResult result = authService.register(request);
        addRefreshTokenCookie(response, result.refreshToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(result.response());
    }

    @PostMapping("/login")
    @ApiMessage("Đăng nhập thành công")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        AuthService.AuthenticationResult result = authService.login(request);
        addRefreshTokenCookie(response, result.refreshToken());
        return ResponseEntity.ok().body(result.response());
    }

    @PostMapping("/google")
    @ApiMessage("Đăng nhập Google thành công")
    public ResponseEntity<AuthResponse> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request,
            HttpServletResponse response) {
        AuthService.AuthenticationResult result = authService.loginWithGoogle(request);
        addRefreshTokenCookie(response, result.refreshToken());
        return ResponseEntity.ok().body(result.response());
    }

    @PostMapping("/refresh")
    @ApiMessage("Làm mới Access Token thành công")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        AuthService.AuthenticationResult result = authService.refreshAccessToken(refreshToken);
        addRefreshTokenCookie(response, result.refreshToken());
        return ResponseEntity.ok().body(result.response());
    }

    @PostMapping("/phone-otp/send")
    @ApiMessage("Đã gửi OTP xác minh số điện thoại")
    public ResponseEntity<PhoneOtpChallengeResponse> sendPhoneOtp(
            @Valid @RequestBody SendPhoneOtpRequest request) {
        return ResponseEntity.ok(phoneOtpService.sendOtp(request));
    }

    @PostMapping("/phone-otp/verify")
    @ApiMessage("Xác minh số điện thoại thành công")
    public ResponseEntity<PhoneVerificationResponse> verifyPhoneOtp(
            @Valid @RequestBody VerifyPhoneOtpRequest request) {
        return ResponseEntity.ok(phoneOtpService.verifyOtp(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        authService.revokeRefreshToken(refreshToken);
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true).secure(secureCookie).sameSite(sameSite)
                .path("/api/auth").maxAge(Duration.ZERO).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.noContent().build();
    }

    private void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(sameSite)
                .path("/api/auth")
                .maxAge(Duration.ofSeconds(refreshTokenExpirationInSeconds))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
