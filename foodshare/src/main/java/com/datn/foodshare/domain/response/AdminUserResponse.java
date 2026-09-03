package com.datn.foodshare.domain.response;

import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.util.constant.AuthProvider;
import com.datn.foodshare.util.constant.Role;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import com.datn.foodshare.util.constant.VerificationStatus;
import com.datn.foodshare.util.constant.OrganizationType;
import com.datn.foodshare.util.constant.SupplierType;
import com.datn.foodshare.util.constant.ProfileType;

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
    private BusinessProfileInfo businessProfile;

    @Getter
    @Builder
    public static class BusinessProfileInfo {
        private String name;
        private String description;
        private String taxCode;
        private VerificationStatus verificationStatus;
        private ProfileType profileType;
        private OrganizationType organizationType;
        private SupplierType supplierType;
        private List<String> licenseUrls;
    }

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
                .businessProfile(user.getBusinessProfile() != null ? BusinessProfileInfo.builder()
                        .name(user.getBusinessProfile().getName())
                        .description(user.getBusinessProfile().getDescription())
                        .taxCode(user.getBusinessProfile().getTaxCode())
                        .verificationStatus(user.getBusinessProfile().getVerificationStatus())
                        .profileType(user.getBusinessProfile().getProfileType())
                        .organizationType(user.getBusinessProfile().getOrganizationType())
                        .supplierType(user.getBusinessProfile().getSupplierType())
                        .licenseUrls(user.getBusinessProfile().getLicenses() != null ? 
                                user.getBusinessProfile().getLicenses().stream()
                                        .map(l -> l.getFileUrl()).collect(Collectors.toList()) : null)
                        .build() : null)
                .build();
    }
}
