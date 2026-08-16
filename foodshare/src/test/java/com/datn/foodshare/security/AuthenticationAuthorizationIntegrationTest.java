package com.datn.foodshare.security;

import com.datn.foodshare.config.SecurityConfiguration;
import com.datn.foodshare.controller.AuthController;
import com.datn.foodshare.domain.response.AuthResponse;
import com.datn.foodshare.service.AuthService;
import com.datn.foodshare.service.CustomUserDetailsService;
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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AuthController.class, AuthorizationProbeController.class})
@Import({
        SecurityConfiguration.class,
        JwtAuthenticationFilter.class,
        CustomAuthenticationEntryPoint.class,
        CustomAccessDeniedHandler.class
})
class AuthenticationAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

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
                        .content("{\"phone\":\"0901234567\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=refresh-token")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")));
    }

    @Test
    void refreshReadsTokenFromCookieAndReturnsNewAccessToken() throws Exception {
        when(authService.refreshAccessToken("refresh-token")).thenReturn(authResponse("new-access-token"));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"));
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
