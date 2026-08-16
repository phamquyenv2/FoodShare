package com.datn.foodshare.domain.response;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.util.constant.AuthProvider;
import com.datn.foodshare.util.constant.Role;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class CurrentUserResponse {

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
    private boolean profileCompleted;
    private UserProfileResponse profile;

    public static CurrentUserResponse from(User user, BusinessProfile businessProfile) {
        return CurrentUserResponse.builder()
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
                .profileCompleted(user.isProfileCompleted())
                .profile(UserProfileResponse.from(businessProfile))
                .build();
    }
}
