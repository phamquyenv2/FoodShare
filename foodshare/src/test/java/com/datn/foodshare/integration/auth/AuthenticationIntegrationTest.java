package com.datn.foodshare.integration.auth;

import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.integration.IntegrationTestSupport;
import com.datn.foodshare.repository.UserTokenRepository;
import com.datn.foodshare.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthenticationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserTokenRepository userTokenRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void registerLoginRefreshAndProtectedRequest_useRealSecurityAndDatabase() throws Exception {
        String phone = uniquePhone();
        String password = "StrongPassword123";

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "phone": "%s",
                                  "registrationToken": "%s",
                                  "password": "%s",
                                  "fullName": "QuyenPA Integration Recipient",
                                  "email": "%s@quyenpa.test",
                                  "role": "RECIPIENT"
                                }
                                """.formatted(phone, jwtTokenProvider.createPhoneRegistrationToken(phone),
                                password, UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.role").value("RECIPIENT"))
                .andReturn();

        User savedUser = userRepository.findByPhone(phone).orElseThrow();
        assertThat(savedUser.getPasswordHash()).isNotEqualTo(password);
        assertThat(passwordEncoder.matches(password, savedUser.getPasswordHash())).isTrue();
        assertThat(userTokenRepository.findAll())
                .anySatisfy(token -> assertThat(token.getUser().getId()).isEqualTo(savedUser.getId()));

        String registeredAccessToken = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + registeredAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(savedUser.getId()))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(phone, password)))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();
        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.userId").value(savedUser.getId()));
    }

    @Test
    void duplicateRegistration_isRejectedWithoutCreatingAnotherUser() throws Exception {
        String phone = uniquePhone();
        String request = """
                {
                  "phone": "%s",
                  "registrationToken": "%s",
                  "password": "StrongPassword123",
                  "fullName": "QuyenPA Duplicate User",
                  "role": "RECIPIENT"
                }
                """.formatted(phone, jwtTokenProvider.createPhoneRegistrationToken(phone));

        mockMvc.perform(post("/api/auth/register").contentType("application/json").content(request))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/register").contentType("application/json").content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400));

        assertThat(userRepository.findAll().stream().filter(user -> phone.equals(user.getPhone()))).hasSize(1);
    }

    private String uniquePhone() {
        long number = Integer.toUnsignedLong(UUID.randomUUID().hashCode()) % 100_000_000L;
        return "09" + String.format("%08d", number);
    }
}
