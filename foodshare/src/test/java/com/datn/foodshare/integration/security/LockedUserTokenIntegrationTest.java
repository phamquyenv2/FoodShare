package com.datn.foodshare.integration.security;

import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.integration.IntegrationTestSupport;
import com.datn.foodshare.util.constant.Role;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LockedUserTokenIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void lockedUserCannotReuseAccessToken() throws Exception {
        User recipient = createUser(Role.RECIPIENT, true);
        recipient.setPasswordHash(passwordEncoder.encode("Password123"));
        userRepository.saveAndFlush(recipient);
        User admin = createUser(Role.ADMIN, true);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"identifier":"%s","password":"Password123"}
                                """.formatted(recipient.getPhone())))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.data.accessToken");

        mockMvc.perform(patch("/api/admin/users/{id}/status", recipient.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType("application/json")
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }
}
