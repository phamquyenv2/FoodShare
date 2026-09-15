package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.util.SecurityUtil;
import com.datn.foodshare.util.constant.Role;
import com.datn.foodshare.util.error.PermissionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void currentUserReturnsAuthenticatedActiveRecord() {
        User user = user(2L, Role.RECIPIENT);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        try (MockedStatic<SecurityUtil> security = mockStatic(SecurityUtil.class)) {
            security.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(2L));

            assertEquals(user, new PermissionService(userRepository).currentUser());
        }
    }

    @Test
    void currentUserRejectsMissingAuthenticationAndDeletedAccount() {
        PermissionService service = new PermissionService(userRepository);

        try (MockedStatic<SecurityUtil> security = mockStatic(SecurityUtil.class)) {
            security.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.empty());
            assertThrows(BadCredentialsException.class, service::currentUser);

            security.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(99L));
            when(userRepository.findById(99L)).thenReturn(Optional.empty());
            assertThrows(BadCredentialsException.class, service::currentUser);
        }
    }

    @Test
    void requireRoleAcceptsExpectedRoleAndRejectsDifferentRole() {
        PermissionService service = new PermissionService(userRepository);
        User supplier = user(3L, Role.SUPPLIER);

        assertDoesNotThrow(() -> service.requireRole(supplier, Role.SUPPLIER));
        assertThrows(PermissionException.class,
                () -> service.requireRole(supplier, Role.ADMIN));
    }

    @Test
    void requireReceiverAcceptsRecipientAndOrganizationOnly() {
        PermissionService service = new PermissionService(userRepository);

        assertDoesNotThrow(() -> service.requireReceiver(user(2L, Role.RECIPIENT)));
        assertDoesNotThrow(() -> service.requireReceiver(user(4L, Role.ORGANIZATION)));
        assertThrows(PermissionException.class,
                () -> service.requireReceiver(user(3L, Role.SUPPLIER)));
    }

    @Test
    void requireFoodPostOwnershipRejectsAnotherSupplierAndMalformedPost() {
        PermissionService service = new PermissionService(userRepository);
        User owner = user(3L, Role.SUPPLIER);
        BusinessProfile profile = new BusinessProfile();
        profile.setUser(owner);
        FoodPost post = new FoodPost();
        post.setBusinessProfile(profile);

        assertDoesNotThrow(() -> service.requireFoodPostOwnership(owner, post));
        assertThrows(PermissionException.class,
                () -> service.requireFoodPostOwnership(user(5L, Role.SUPPLIER), post));
        assertThrows(PermissionException.class,
                () -> service.requireFoodPostOwnership(owner, new FoodPost()));
    }

    private User user(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }
}
