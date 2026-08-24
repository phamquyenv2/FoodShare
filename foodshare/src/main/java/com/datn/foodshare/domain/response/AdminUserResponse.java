package com.datn.foodshare.domain.response;

import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.util.constant.AuthProvider;
import com.datn.foodshare.util.constant.Role;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
public class AdminUserResponse {

    private Long id;
    private String fullName;
    private String phone;
    private String email;
    private String avatarUrl;
    private String specificAddress;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Role role;
    private AuthProvider authProvider;
    private boolean active;
    private boolean profileCompleted;
    private Instant createdAt;
    private Instant updatedAt;

    public static AdminUserResponse from(User user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .specificAddress(user.getSpecificAddress())
                .latitude(user.getLatitude())
                .longitude(user.getLongitude())
                .role(user.getRole())
                .authProvider(user.getAuthProvider())
                .active(user.isActive())
                .profileCompleted(user.isProfileCompleted())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
