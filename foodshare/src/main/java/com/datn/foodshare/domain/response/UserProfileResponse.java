package com.datn.foodshare.domain.response;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.util.constant.OrganizationType;
import com.datn.foodshare.util.constant.ProfileType;
import com.datn.foodshare.util.constant.SupplierType;
import com.datn.foodshare.util.constant.VerificationStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserProfileResponse {

    private Long id;
    private String name;
    private String description;
    private String taxCode;
    private VerificationStatus verificationStatus;
    private ProfileType profileType;
    private SupplierType supplierType;
    private OrganizationType organizationType;

    public static UserProfileResponse from(BusinessProfile profile) {
        if (profile == null) {
            return null;
        }
        return UserProfileResponse.builder()
                .id(profile.getId())
                .name(profile.getName())
                .description(profile.getDescription())
                .taxCode(profile.getTaxCode())
                .verificationStatus(profile.getVerificationStatus())
                .profileType(profile.getProfileType())
                .supplierType(profile.getSupplierType())
                .organizationType(profile.getOrganizationType())
                .build();
    }
}
