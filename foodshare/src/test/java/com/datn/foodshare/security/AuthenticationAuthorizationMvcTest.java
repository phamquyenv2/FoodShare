package com.datn.foodshare.security;

import com.datn.foodshare.config.SecurityConfiguration;
import com.datn.foodshare.controller.AuthController;
import com.datn.foodshare.domain.response.AuthResponse;
import com.datn.foodshare.domain.response.PhoneOtpChallengeResponse;
import com.datn.foodshare.domain.response.PhoneVerificationResponse;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.service.AuthService;
import com.datn.foodshare.service.CustomUserDetailsService;
import com.datn.foodshare.service.PhoneOtpService;
import com.datn.foodshare.util.constant.AuthProvider;
import com.datn.foodshare.util.constant.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AuthController.class, AuthorizationProbeController.class})
@Import({
        SecurityConfiguration.class,
        JwtAuthenticationFilter.class,
        RateLimitFilter.class,
        CustomAuthenticationEntryPoint.class,
        CustomAccessDeniedHandler.class
})
class AuthenticationAuthorizationMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private PhoneOtpService phoneOtpService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void anonymousRequestToProtectedApiReturns401() throws Exception {
        mockMvc.perform(get("/api/recipient/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.statusCode").value(401));
    }

    @Test
    void invalidBearerTokenReturns401() throws Exception {
        when(jwtTokenProvider.validateAccessToken("invalid-token")).thenReturn(false);

        mockMvc.perform(get("/api/recipient/probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredBearerTokenReturns401() throws Exception {
        when(jwtTokenProvider.validateAccessToken("expired-token")).thenReturn(false);

        mockMvc.perform(get("/api/recipient/probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenCannotAuthenticateProtectedApi() throws Exception {
        when(jwtTokenProvider.validateAccessToken("refresh-token")).thenReturn(false);

        mockMvc.perform(get("/api/recipient/probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer refresh-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validAdminAccessTokenCanAccessAdminApi() throws Exception {
        when(jwtTokenProvider.validateAccessToken("admin-access-token")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken("admin-access-token")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser()));
        when(jwtTokenProvider.getAuthentication("admin-access-token"))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated(
                        "1",
                        "admin-access-token",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        mockMvc.perform(get("/api/admin/probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-access-token"))
                .andExpect(status().isOk());
    }

    @Test
    void accessTokenIsRejectedImmediatelyAfterUserIsLocked() throws Exception {
        when(jwtTokenProvider.validateAccessToken("locked-user-token")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken("locked-user-token")).thenReturn(1L);
        User lockedUser = activeUser();
        lockedUser.setActive(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(lockedUser));

        mockMvc.perform(get("/api/recipient/probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer locked-user-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recipientCanAccessAuthenticatedApi() throws Exception {
        mockMvc.perform(get("/api/recipient/probe").with(user("recipient").roles("RECIPIENT")))
                .andExpect(status().isOk());
    }

    @Test
    void recipientCannotAccessAdminApi() throws Exception {
        mockMvc.perform(get("/api/admin/probe").with(user("recipient").roles("RECIPIENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.statusCode").value(403));
    }

    @Test
    void supplierCannotAccessAdminApi() throws Exception {
        mockMvc.perform(get("/api/admin/probe").with(user("supplier").roles("SUPPLIER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void registerRejectsInvalidInput() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("{\"phone\":\"\",\"password\":\"short\",\"fullName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400));
    }

    @Test
    void loginSetsRefreshTokenInHttpOnlyCookie() throws Exception {
        AuthResponse authResponse = authResponse("access-token");
        when(authService.login(any())).thenReturn(
                new AuthService.AuthenticationResult(authResponse, "refresh-token"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"identifier\":\"0901234567\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=refresh-token")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")));
    }

    @Test
    void refreshReadsTokenFromCookieAndReturnsNewAccessToken() throws Exception {
        when(authService.refreshAccessToken("refresh-token")).thenReturn(
                new AuthService.AuthenticationResult(authResponse("new-access-token"), "rotated-refresh-token"));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=rotated-refresh-token")));
    }

    @Test
    void phoneOtpEndpointsExposeChallengeAndRegistrationTokens() throws Exception {
        when(phoneOtpService.sendOtp(any())).thenReturn(
                new PhoneOtpChallengeResponse("challenge-token", "******678", 900));
        when(phoneOtpService.verifyOtp(any())).thenReturn(
                new PhoneVerificationResponse("registration-token", "0912345678", 600));

        mockMvc.perform(post("/api/auth/phone-otp/send")
                        .contentType("application/json")
                        .content("{\"phone\":\"0912345678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.challengeToken").value("challenge-token"))
                .andExpect(jsonPath("$.data.maskedPhone").value("******678"));

        mockMvc.perform(post("/api/auth/phone-otp/verify")
                        .contentType("application/json")
                        .content("{\"challengeToken\":\"challenge-token\",\"otp\":\"1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registrationToken").value("registration-token"))
                .andExpect(jsonPath("$.data.phone").value("0912345678"));
    }

    @Test
    void logoutRevokesRefreshTokenAndClearsCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("refresh_token", "refresh-token")))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        verify(authService).revokeRefreshToken("refresh-token");
    }

    private AuthResponse authResponse(String accessToken) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .userId(1L)
                .phone("0901234567")
                .fullName("Test User")
                .role(Role.RECIPIENT)
                .authProvider(AuthProvider.LOCAL)
                .build();
    }

    private User activeUser() {
        User user = new User();
        user.setId(1L);
        user.setActive(true);
        return user;
    }
}

@RestController
class AuthorizationProbeController {

    @GetMapping("/api/admin/probe")
    String adminProbe() {
        return "admin";
    }

    @GetMapping("/api/recipient/probe")
    String recipientProbe() {
        return "recipient";
    }
}
