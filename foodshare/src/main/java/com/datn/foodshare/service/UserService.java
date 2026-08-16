package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.request.UpdateProfileRequest;
import com.datn.foodshare.domain.request.UpdateUserRequest;
import com.datn.foodshare.domain.response.CurrentUserResponse;
import com.datn.foodshare.repository.BusinessProfileRepository;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.util.SecurityUtil;
import com.datn.foodshare.util.constant.ProfileType;
import com.datn.foodshare.util.constant.Role;
import com.datn.foodshare.util.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^(?:\\+84|0)(3|5|7|8|9)[0-9]{8}$");

    private final UserRepository userRepository;
    private final BusinessProfileRepository businessProfileRepository;

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser() {
        User user = getAuthenticatedUser();
        return toResponse(user);
    }

    @Transactional
    public CurrentUserResponse updateCurrentUser(UpdateUserRequest request) {
        User user = getAuthenticatedUser();

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getEmail() != null) {
            String email = normalizeEmail(request.getEmail());
            if (email != null && userRepository.existsByEmailAndIdNot(email, user.getId())) {
                throw new BusinessException("Email đã được sử dụng");
            }
            user.setEmail(email);
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(trimToNull(request.getAvatarUrl()));
        }

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public CurrentUserResponse updateProfile(UpdateProfileRequest request) {
        User user = getAuthenticatedUser();
        if (user.getRole() == Role.ADMIN) {
            throw new BusinessException("ADMIN không có hồ sơ nghiệp vụ trong chức năng này");
        }

        updatePhoneWhenRequired(user, request.getPhone());
        validateRequiredUserFields(user);
        validateLocationPair(request.getLatitude(), request.getLongitude());
        user.setSpecificAddress(request.getSpecificAddress().trim());
        user.setLatitude(request.getLatitude());
        user.setLongitude(request.getLongitude());

        BusinessProfile businessProfile = switch (user.getRole()) {
            case RECIPIENT -> completeRecipientProfile(request);
            case SUPPLIER -> upsertSupplierProfile(user, request);
            case ORGANIZATION -> upsertOrganizationProfile(user, request);
            case ADMIN -> throw new BusinessException("ADMIN không có hồ sơ nghiệp vụ trong chức năng này");
        };

        user.setProfileCompleted(true);
        User savedUser = userRepository.save(user);
        return CurrentUserResponse.from(savedUser, businessProfile);
    }

    private BusinessProfile completeRecipientProfile(UpdateProfileRequest request) {
        if (hasText(request.getName())
                || hasText(request.getDescription())
                || hasText(request.getTaxCode())
                || request.getSupplierType() != null
                || request.getOrganizationType() != null) {
            throw new BusinessException("RECIPIENT không sử dụng thông tin BusinessProfile");
        }
        return null;
    }

    private BusinessProfile upsertSupplierProfile(User user, UpdateProfileRequest request) {
        requireProfileName(request.getName(), "Tên Nhà cung cấp là bắt buộc");
        if (request.getSupplierType() == null) {
            throw new BusinessException("Loại Nhà cung cấp là bắt buộc");
        }
        if (request.getOrganizationType() != null) {
            throw new BusinessException("SUPPLIER không được gửi organizationType");
        }

        BusinessProfile profile = getOrCreateBusinessProfile(user, ProfileType.SUPPLIER);
        applyBusinessProfileFields(profile, request);
        profile.setProfileType(ProfileType.SUPPLIER);
        profile.setSupplierType(request.getSupplierType());
        profile.setOrganizationType(null);
        return businessProfileRepository.save(profile);
    }

    private BusinessProfile upsertOrganizationProfile(User user, UpdateProfileRequest request) {
        requireProfileName(request.getName(), "Tên Tổ chức là bắt buộc");
        if (request.getOrganizationType() == null) {
            throw new BusinessException("Loại Tổ chức là bắt buộc");
        }
        if (request.getSupplierType() != null) {
            throw new BusinessException("ORGANIZATION không được gửi supplierType");
        }

        BusinessProfile profile = getOrCreateBusinessProfile(user, ProfileType.ORGANIZATION);
        applyBusinessProfileFields(profile, request);
        profile.setProfileType(ProfileType.ORGANIZATION);
        profile.setOrganizationType(request.getOrganizationType());
        profile.setSupplierType(null);
        return businessProfileRepository.save(profile);
    }

    private BusinessProfile getOrCreateBusinessProfile(User user, ProfileType expectedType) {
        BusinessProfile profile = businessProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> BusinessProfile.builder().user(user).profileType(expectedType).build());
        if (profile.getProfileType() != null && profile.getProfileType() != expectedType) {
            throw new BusinessException("Loại hồ sơ hiện tại không khớp với role của User");
        }
        return profile;
    }

    private void applyBusinessProfileFields(BusinessProfile profile, UpdateProfileRequest request) {
        String taxCode = trimToNull(request.getTaxCode());
        if (taxCode != null) {
            businessProfileRepository.findByTaxCode(taxCode)
                    .filter(existing -> profile.getId() == null || !existing.getId().equals(profile.getId()))
                    .ifPresent(existing -> {
                        throw new BusinessException("Mã số thuế đã được sử dụng");
                    });
        }
        profile.setName(request.getName().trim());
        profile.setDescription(trimToNull(request.getDescription()));
        profile.setTaxCode(taxCode);
    }

    private void updatePhoneWhenRequired(User user, String requestedPhone) {
        String phone = trimToNull(requestedPhone);
        if (!hasText(user.getPhone())) {
            if (phone == null) {
                throw new BusinessException("Tài khoản Google phải bổ sung số điện thoại");
            }
            if (userRepository.existsByPhoneAndIdNot(phone, user.getId())) {
                throw new BusinessException("Số điện thoại đã được sử dụng");
            }
            user.setPhone(phone);
            return;
        }
        if (phone != null && !user.getPhone().equals(phone)) {
            throw new BusinessException("Không thể thay đổi số điện thoại qua API hồ sơ");
        }
    }

    private void validateLocationPair(BigDecimal latitude, BigDecimal longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new BusinessException("Latitude và longitude phải được cung cấp cùng nhau");
        }
    }

    private void validateRequiredUserFields(User user) {
        if (!hasText(user.getFullName())) {
            throw new BusinessException("Họ tên là bắt buộc để hoàn thiện hồ sơ");
        }
        if (!hasText(user.getPhone()) || !PHONE_PATTERN.matcher(user.getPhone()).matches()) {
            throw new BusinessException("Số điện thoại không đúng định dạng");
        }
    }

    private void requireProfileName(String name, String message) {
        if (!hasText(name)) {
            throw new BusinessException(message);
        }
    }

    private User getAuthenticatedUser() {
        Long userId = SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new BadCredentialsException("Không xác định được người dùng hiện tại"));
        return userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Tài khoản không tồn tại"));
    }

    private CurrentUserResponse toResponse(User user) {
        BusinessProfile profile = businessProfileRepository.findByUserId(user.getId()).orElse(null);
        return CurrentUserResponse.from(user, profile);
    }

    private String normalizeEmail(String email) {
        String normalized = trimToNull(email);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
