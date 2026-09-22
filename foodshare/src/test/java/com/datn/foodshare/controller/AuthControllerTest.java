package com.datn.foodshare.controller;

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
import com.datn.foodshare.util.constant.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;
    @Mock
    private PhoneOtpService phoneOtpService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService, phoneOtpService);
        ReflectionTestUtils.setField(controller, "refreshTokenExpirationInSeconds", 2592000L);
        ReflectionTestUtils.setField(controller, "secureCookie", false);
        ReflectionTestUtils.setField(controller, "sameSite", "Lax");
    }

    private AuthService.AuthenticationResult mockAuthResult() {
        AuthResponse response = AuthResponse.builder()
                .accessToken("access-token-123")
                .tokenType("Bearer")
                .userId(1L)
                .email("user@example.com")
                .role(Role.RECIPIENT)
                .build();
        return new AuthService.AuthenticationResult(response, "refresh-token-456");
    }

    @Test
    void register_setsCookieAndReturns201() {
        RegisterRequest request = new RegisterRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthService.AuthenticationResult authResult = mockAuthResult();
        when(authService.register(request)).thenReturn(authResult);

        ResponseEntity<AuthResponse> result = controller.register(request, response);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertSame(authResult.response(), result.getBody());
        assertTrue(response.getHeader(HttpHeaders.SET_COOKIE).contains("refresh_token=refresh-token-456"));
    }

    @Test
    void login_setsCookieAndReturns200() {
        LoginRequest request = new LoginRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthService.AuthenticationResult authResult = mockAuthResult();
        when(authService.login(request)).thenReturn(authResult);

        ResponseEntity<AuthResponse> result = controller.login(request, response);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(authResult.response(), result.getBody());
        assertTrue(response.getHeader(HttpHeaders.SET_COOKIE).contains("refresh_token=refresh-token-456"));
    }

    @Test
    void googleLogin_setsCookieAndReturns200() {
        GoogleLoginRequest request = new GoogleLoginRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthService.AuthenticationResult authResult = mockAuthResult();
        when(authService.loginWithGoogle(request)).thenReturn(authResult);

        ResponseEntity<AuthResponse> result = controller.googleLogin(request, response);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(authResult.response(), result.getBody());
        assertTrue(response.getHeader(HttpHeaders.SET_COOKIE).contains("refresh_token=refresh-token-456"));
    }

    @Test
    void refresh_setsCookieAndReturns200() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthService.AuthenticationResult authResult = mockAuthResult();
        when(authService.refreshAccessToken("old-refresh-token")).thenReturn(authResult);

        ResponseEntity<AuthResponse> result = controller.refresh("old-refresh-token", response);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(authResult.response(), result.getBody());
        assertTrue(response.getHeader(HttpHeaders.SET_COOKIE).contains("refresh_token=refresh-token-456"));
    }

    @Test
    void sendPhoneOtp_returns200() {
        SendPhoneOtpRequest request = new SendPhoneOtpRequest("0901234567");
        PhoneOtpChallengeResponse response = new PhoneOtpChallengeResponse("token", "090***123", 300L);
        when(phoneOtpService.sendOtp(request)).thenReturn(response);

        ResponseEntity<PhoneOtpChallengeResponse> result = controller.sendPhoneOtp(request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());
    }

    @Test
    void verifyPhoneOtp_returns200() {
        VerifyPhoneOtpRequest request = new VerifyPhoneOtpRequest("token", "123456");
        PhoneVerificationResponse response = new PhoneVerificationResponse("verified-token", "0901234567", 300L);
        when(phoneOtpService.verifyOtp(request)).thenReturn(response);

        ResponseEntity<PhoneVerificationResponse> result = controller.verifyPhoneOtp(request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());
    }

    @Test
    void logout_revokesTokenClearsCookieAndReturns204() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<Void> result = controller.logout("refresh-to-revoke", response);

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        verify(authService).revokeRefreshToken("refresh-to-revoke");
        assertTrue(response.getHeader(HttpHeaders.SET_COOKIE).contains("Max-Age=0"));
    }
}
