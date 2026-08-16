package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.request.UpdateProfileRequest;
import com.datn.foodshare.domain.request.UpdateUserRequest;
import com.datn.foodshare.repository.BusinessProfileRepository;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.util.constant.AuthProvider;
import com.datn.foodshare.util.constant.OrganizationType;
import com.datn.foodshare.util.constant.ProfileType;
import com.datn.foodshare.util.constant.Role;
import com.datn.foodshare.util.constant.SupplierType;
import com.datn.foodshare.util.error.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BusinessProfileRepository businessProfileRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, businessProfileRepository);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("1", null, java.util.List.of()));
        lenient().when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(businessProfileRepository.save(any(BusinessProfile.class))).thenAnswer(invocation -> {
            BusinessProfile profile = invocation.getArgument(0);
            if (profile.getId() == null) {
                profile.setId(10L);
            }
            return profile;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUserUsesAuthenticatedPrincipalAndReturnsSafeResponse() {
        User user = user(Role.RECIPIENT, AuthProvider.LOCAL, "0901234567");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(businessProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());

        var response = userService.getCurrentUser();

        assertEquals(1L, response.getId());
        assertEquals(Role.RECIPIENT, response.getRole());
        assertNull(response.getProfile());
        verify(userRepository).findById(1L);
    }

    @Test
    void updateCurrentUserUpdatesOnlyAllowedCommonFields() {
        User user = user(Role.RECIPIENT, AuthProvider.LOCAL, "0901234567");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(businessProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());
        UpdateUserRequest request = new UpdateUserRequest();
        request.setFullName("  New Name  ");
        request.setEmail("User@Example.com");
        request.setAvatarUrl(" https://example.com/Avatar.PNG ");

        var response = userService.updateCurrentUser(request);

        assertEquals("New Name", response.getFullName());
        assertEquals("user@example.com", response.getEmail());
        assertEquals("https://example.com/Avatar.PNG", response.getAvatarUrl());
        assertEquals(Role.RECIPIENT, response.getRole());
        assertFalse(response.isProfileCompleted());
    }

    @Test
    void updateCurrentUserRejectsDuplicateEmail() {
        User user = user(Role.RECIPIENT, AuthProvider.LOCAL, "0901234567");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot("used@example.com", 1L)).thenReturn(true);
        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail("used@example.com");

        assertThrows(BusinessException.class, () -> userService.updateCurrentUser(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void completeRecipientProfileStoresCommonLocationAndMarksCompleted() {
        User user = user(Role.RECIPIENT, AuthProvider.LOCAL, "0901234567");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        UpdateProfileRequest request = recipientProfileRequest();

        var response = userService.updateProfile(request);

        assertTrue(response.isProfileCompleted());
        assertEquals("Đà Nẵng", response.getSpecificAddress());
        assertNull(response.getProfile());
        verify(businessProfileRepository, never()).save(any());
    }

    @Test
    void completeRecipientProfileRejectsBusinessFields() {
        User user = user(Role.RECIPIENT, AuthProvider.LOCAL, "0901234567");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        UpdateProfileRequest request = recipientProfileRequest();
        request.setName("Unexpected business");

        assertThrows(BusinessException.class, () -> userService.updateProfile(request));
        assertFalse(user.isProfileCompleted());
        verify(userRepository, never()).save(any());
    }

    @Test
    void completeSupplierProfileCreatesBusinessProfileAndMarksCompleted() {
        User user = user(Role.SUPPLIER, AuthProvider.LOCAL, "0901234567");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(businessProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());
        UpdateProfileRequest request = recipientProfileRequest();
        request.setName("Nhà hàng FoodShare");
        request.setDescription("Thực phẩm trong ngày");
        request.setSupplierType(SupplierType.RESTAURANT);

        var response = userService.updateProfile(request);

        assertTrue(response.isProfileCompleted());
        assertEquals(ProfileType.SUPPLIER, response.getProfile().getProfileType());
        assertEquals(SupplierType.RESTAURANT, response.getProfile().getSupplierType());
        assertNull(response.getProfile().getOrganizationType());
    }

    @Test
    void completeSupplierProfileRejectsMissingSupplierType() {
        User user = user(Role.SUPPLIER, AuthProvider.LOCAL, "0901234567");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        UpdateProfileRequest request = recipientProfileRequest();
        request.setName("Nhà cung cấp");

        assertThrows(BusinessException.class, () -> userService.updateProfile(request));
        verify(businessProfileRepository, never()).save(any());
    }

    @Test
    void completeOrganizationProfileCreatesBusinessProfileAndMarksCompleted() {
        User user = user(Role.ORGANIZATION, AuthProvider.LOCAL, "0901234567");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(businessProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());
        UpdateProfileRequest request = recipientProfileRequest();
        request.setName("Tổ chức Thiện nguyện");
        request.setOrganizationType(OrganizationType.CHARITY);
        request.setTaxCode("TAX-001");
        when(businessProfileRepository.findByTaxCode("TAX-001")).thenReturn(Optional.empty());

        var response = userService.updateProfile(request);

        assertTrue(response.isProfileCompleted());
        assertEquals(ProfileType.ORGANIZATION, response.getProfile().getProfileType());
        assertEquals(OrganizationType.CHARITY, response.getProfile().getOrganizationType());
        assertEquals("TAX-001", response.getProfile().getTaxCode());
    }

    @Test
    void googleUserCannotCompleteProfileWithoutPhone() {
        User user = user(Role.RECIPIENT, AuthProvider.GOOGLE, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(BusinessException.class, () -> userService.updateProfile(recipientProfileRequest()));
        assertFalse(user.isProfileCompleted());
        verify(userRepository, never()).save(any());
    }

    @Test
    void googleUserCanAddUniquePhoneAndCompleteProfile() {
        User user = user(Role.RECIPIENT, AuthProvider.GOOGLE, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        UpdateProfileRequest request = recipientProfileRequest();
        request.setPhone("0912345678");

        var response = userService.updateProfile(request);

        assertEquals("0912345678", response.getPhone());
        assertTrue(response.isProfileCompleted());
        verify(userRepository).existsByPhoneAndIdNot("0912345678", 1L);
    }

    @Test
    void profileRejectsIncompleteLocationPair() {
        User user = user(Role.RECIPIENT, AuthProvider.LOCAL, "0901234567");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        UpdateProfileRequest request = recipientProfileRequest();
        request.setLongitude(null);

        assertThrows(BusinessException.class, () -> userService.updateProfile(request));
        assertFalse(user.isProfileCompleted());
    }

    @Test
    void localUserWithInvalidStoredPhoneCannotCompleteProfile() {
        User user = user(Role.RECIPIENT, AuthProvider.LOCAL, "123");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(BusinessException.class, () -> userService.updateProfile(recipientProfileRequest()));
        assertFalse(user.isProfileCompleted());
    }

    private User user(Role role, AuthProvider provider, String phone) {
        return User.builder()
                .id(1L)
                .phone(phone)
                .email(provider == AuthProvider.GOOGLE ? "google@example.com" : null)
                .passwordHash(provider == AuthProvider.LOCAL ? "hash" : null)
                .fullName("Test User")
                .role(role)
                .authProvider(provider)
                .active(true)
                .profileCompleted(false)
                .build();
    }

    private UpdateProfileRequest recipientProfileRequest() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setSpecificAddress("Đà Nẵng");
        request.setLatitude(new BigDecimal("16.0544"));
        request.setLongitude(new BigDecimal("108.2022"));
        return request;
    }
}
