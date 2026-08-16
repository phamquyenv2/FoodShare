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
import com.datn.foodshare.domain.response.AuthResponse;
import com.datn.foodshare.service.AuthService;
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
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {
        return ResponseEntity.ok().body(authService.refreshAccessToken(refreshToken));
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
