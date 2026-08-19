package com.datn.foodshare.controller;

import com.datn.foodshare.config.SecurityConfiguration;
import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.response.CurrentUserResponse;
import com.datn.foodshare.security.CustomAccessDeniedHandler;
import com.datn.foodshare.security.CustomAuthenticationEntryPoint;
import com.datn.foodshare.security.JwtAuthenticationFilter;
import com.datn.foodshare.security.JwtTokenProvider;
import com.datn.foodshare.service.CustomUserDetailsService;
import com.datn.foodshare.service.UserService;
import com.datn.foodshare.util.constant.AuthProvider;
import com.datn.foodshare.util.constant.ProfileType;
import com.datn.foodshare.util.constant.Role;
import com.datn.foodshare.util.constant.SupplierType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UserController.class)
@Import({
        SecurityConfiguration.class,
        JwtAuthenticationFilter.class,
        CustomAuthenticationEntryPoint.class,
        CustomAccessDeniedHandler.class
})
class UserProfileIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void anonymousCannotGetCurrentUser() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.statusCode").value(401));
    }

    @Test
    void authenticatedUserCanGetOwnUserAndProfileWithoutSensitiveFields() throws Exception {
        stubAccessToken("valid-token", Role.SUPPLIER);
        when(userService.getCurrentUser()).thenReturn(supplierResponse());

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.role").value("SUPPLIER"))
                .andExpect(jsonPath("$.data.profile.profileType").value("SUPPLIER"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.googleSubject").doesNotExist())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
    }

    @Test
    void authenticatedUserCanPatchAllowedCommonFields() throws Exception {
        stubAccessToken("valid-token", Role.RECIPIENT);
        CurrentUserResponse response = CurrentUserResponse.builder()
                .id(1L)
                .fullName("Tên mới")
                .email("new@example.com")
                .role(Role.RECIPIENT)
                .authProvider(AuthProvider.LOCAL)
                .profileCompleted(false)
                .build();
        when(userService.updateCurrentUser(any())).thenReturn(response);

        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                        .contentType("application/json")
                        .content("{\"fullName\":\"Tên mới\",\"email\":\"new@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Tên mới"))
                .andExpect(jsonPath("$.data.role").value("RECIPIENT"));

        verify(userService).updateCurrentUser(any());
    }

    @Test
    void patchRejectsInvalidCommonData() throws Exception {
        stubAccessToken("valid-token", Role.RECIPIENT);

        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                        .contentType("application/json")
                        .content("{\"fullName\":\"   \",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400));
    }

    @Test
    void profileRejectsMissingAddressAndInvalidPhone() throws Exception {
        stubAccessToken("valid-token", Role.RECIPIENT);

        mockMvc.perform(put("/api/users/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                        .contentType("application/json")
                        .content("{\"phone\":\"123\",\"specificAddress\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400));
    }

    @Test
    void authenticatedUserCanCompleteRoleProfile() throws Exception {
        stubAccessToken("valid-token", Role.SUPPLIER);
        when(userService.updateProfile(any())).thenReturn(supplierResponse());

        mockMvc.perform(put("/api/users/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                        .contentType("application/json")
                        .content("""
                                {
                                  "specificAddress": "Đà Nẵng",
                                  "latitude": 16.0544,
                                  "longitude": 108.2022,
                                  "name": "Nhà hàng FoodShare",
                                  "supplierType": "RESTAURANT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileCompleted").value(true))
                .andExpect(jsonPath("$.data.profile.supplierType").value("RESTAURANT"));
    }

    private void stubAccessToken(String token, Role role) {
        when(jwtTokenProvider.validateAccessToken(token)).thenReturn(true);
        when(jwtTokenProvider.getAuthentication(token)).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated(
                        "1",
                        token,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private CurrentUserResponse supplierResponse() {
        User user = User.builder()
                .id(1L)
                .phone("0901234567")
                .fullName("Supplier")
                .role(Role.SUPPLIER)
                .authProvider(AuthProvider.LOCAL)
                .active(true)
                .profileCompleted(true)
                .build();
        BusinessProfile profile = BusinessProfile.builder()
                .id(10L)
                .user(user)
                .name("Nhà hàng FoodShare")
                .profileType(ProfileType.SUPPLIER)
                .supplierType(SupplierType.RESTAURANT)
                .build();
        return CurrentUserResponse.from(user, profile);
    }
}
