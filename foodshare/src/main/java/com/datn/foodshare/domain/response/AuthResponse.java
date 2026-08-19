package com.datn.foodshare.domain.response;

import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.util.constant.AuthProvider;
import com.datn.foodshare.util.constant.Role;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthResponse {

    private String accessToken;

    @Builder.Default
    private String tokenType = "Bearer";

    private Long userId;
    private String phone;
    private String email;
    private String fullName;
    private Role role;
    private AuthProvider authProvider;
    private boolean profileCompleted;

    public static AuthResponse from(User user, String accessToken) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .userId(user.getId())
                .phone(user.getPhone())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .authProvider(user.getAuthProvider())
                .profileCompleted(user.isProfileCompleted())
                .build();
    }
}
