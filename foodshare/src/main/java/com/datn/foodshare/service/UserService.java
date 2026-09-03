package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.request.UpdateProfileRequest;
import com.datn.foodshare.domain.request.UpdateUserRequest;
import com.datn.foodshare.domain.request.UpdateUserStatusRequest;
import com.datn.foodshare.domain.response.AdminUserResponse;
import com.datn.foodshare.domain.response.CurrentUserResponse;
import com.datn.foodshare.repository.BusinessProfileRepository;
import com.datn.foodshare.repository.NotificationRepository;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.service.matching.DynamicMatchingGraphSynchronizer;
import com.datn.foodshare.util.SecurityUtil;
import com.datn.foodshare.util.constant.NotificationReferenceType;
import com.datn.foodshare.util.constant.NotificationType;
import com.datn.foodshare.util.constant.ProfileType;
import com.datn.foodshare.util.constant.Role;
import com.datn.foodshare.domain.entity.Notification;
import com.datn.foodshare.util.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^(?:\\+84|0)(3|5|7|8|9)[0-9]{8}$");

    private final UserRepository userRepository;
    private final BusinessProfileRepository businessProfileRepository;
    private final NotificationRepository notificationRepository;
    private final DynamicMatchingGraphSynchronizer matchingGraphSynchronizer;

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
        
        if (request.getRole() != null && request.getRole() != Role.ADMIN) {
            user.setRole(request.getRole());
        }

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

        boolean isFirstTimeCompleted = !user.isProfileCompleted();
        user.setProfileCompleted(true);
        User savedUser = userRepository.save(user);

        if (isFirstTimeCompleted && (user.getRole() == Role.SUPPLIER || user.getRole() == Role.ORGANIZATION)) {
            // Notify admins about new supplier/org
            List<User> admins = userRepository.findByRole(Role.ADMIN);
            for (User admin : admins) {
                notificationRepository.save(Notification.builder()
                        .user(admin)
                        .title("Đăng ký hồ sơ mới")
                        .content("Người dùng " + user.getFullName() + " vừa hoàn tất hồ sơ đăng ký " + user.getRole() + ". Vui lòng kiểm tra và xét duyệt.")
                        .notificationType(NotificationType.NEW_SUPPLIER)
                        .referenceType(NotificationReferenceType.USER)
                        .referenceId(user.getId())
                        .build());
            }
        }

        matchingGraphSynchronizer.userChangedAfterCommit(savedUser.getId());
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

    // ── Admin User Management ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> adminGetAllUsers(Role role, Boolean active, com.datn.foodshare.util.constant.VerificationStatus verificationStatus, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> cb.conjunction();

        if (role != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("role"), role));
        }
        if (active != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("active"), active));
        }
        if (verificationStatus != null) {
            spec = spec.and((root, query, cb) -> {
                jakarta.persistence.criteria.Join<User, BusinessProfile> businessProfileJoin = root.join("businessProfile", jakarta.persistence.criteria.JoinType.INNER);
                return cb.equal(businessProfileJoin.get("verificationStatus"), verificationStatus);
            });
        }

        return userRepository.findAll(spec, pageable).map(AdminUserResponse::from);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse adminGetUserDetail(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy người dùng với id: " + userId));
        return AdminUserResponse.from(user);
    }

    @Transactional
    public AdminUserResponse adminUpdateUserStatus(Long userId, UpdateUserStatusRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy người dùng với id: " + userId));

        if (user.getRole() == Role.ADMIN) {
            throw new BusinessException("Không thể thay đổi trạng thái tài khoản ADMIN");
        }

        user.setActive(request.getActive());
        return AdminUserResponse.from(userRepository.save(user));
    }

    @Transactional
    public AdminUserResponse adminVerifyBusinessProfile(Long userId, com.datn.foodshare.domain.request.UpdateVerificationStatusRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy người dùng với id: " + userId));

        BusinessProfile profile = user.getBusinessProfile();
        if (profile == null) {
            throw new BusinessException("Người dùng chưa có hồ sơ kinh doanh");
        }

        profile.setVerificationStatus(request.getVerificationStatus());
        businessProfileRepository.save(profile);

        return AdminUserResponse.from(user);
    }
}
