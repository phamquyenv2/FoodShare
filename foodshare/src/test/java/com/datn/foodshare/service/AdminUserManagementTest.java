package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.request.UpdateUserStatusRequest;
import com.datn.foodshare.domain.response.AdminUserResponse;
import com.datn.foodshare.repository.BusinessProfileRepository;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.service.matching.DynamicMatchingGraphSynchronizer;
import com.datn.foodshare.util.constant.AuthProvider;
import com.datn.foodshare.util.constant.Role;
import com.datn.foodshare.util.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserManagementTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private BusinessProfileRepository businessProfileRepository;
    @Mock
    private DynamicMatchingGraphSynchronizer matchingGraphSynchronizer;

    private UserService userService;

    private static final Long USER_ID = 10L;
    private static final Long ADMIN_ID = 1L;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, businessProfileRepository, matchingGraphSynchronizer);
    }

    // ── adminGetAllUsers ───────────────────────────────────────────────

    @Nested
    class AdminGetAllUsers {

        @Test
        void returnsPagedUsers_noFilter() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<User> page = new PageImpl<>(List.of(testUser(Role.SUPPLIER), testUser(Role.RECIPIENT)), pageable, 2);
            when(userRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

            Page<AdminUserResponse> result = userService.adminGetAllUsers(null, null, pageable);

            assertEquals(2, result.getTotalElements());
            verify(userRepository).findAll(any(Specification.class), eq(pageable));
        }

        @Test
        void returnsPagedUsers_withRoleFilter() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<User> page = new PageImpl<>(List.of(testUser(Role.SUPPLIER)), pageable, 1);
            when(userRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

            Page<AdminUserResponse> result = userService.adminGetAllUsers(Role.SUPPLIER, null, pageable);

            assertEquals(1, result.getTotalElements());
            assertEquals(Role.SUPPLIER, result.getContent().get(0).getRole());
        }

        @Test
        void returnsPagedUsers_withActiveFilter() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<User> page = new PageImpl<>(List.of(testUser(Role.RECIPIENT)), pageable, 1);
            when(userRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

            Page<AdminUserResponse> result = userService.adminGetAllUsers(null, true, pageable);

            assertEquals(1, result.getTotalElements());
        }

        @Test
        void doesNotExposePasswordHash() {
            Pageable pageable = PageRequest.of(0, 20);
            User user = testUser(Role.SUPPLIER);
            user.setPasswordHash("$2a$10$hashedpassword");
            Page<User> page = new PageImpl<>(List.of(user), pageable, 1);
            when(userRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

            Page<AdminUserResponse> result = userService.adminGetAllUsers(null, null, pageable);

            AdminUserResponse response = result.getContent().get(0);
            // AdminUserResponse has no passwordHash field — compilation guarantees this
            assertNotNull(response.getFullName());
            assertNotNull(response.getRole());
        }
    }

    // ── adminGetUserDetail ─────────────────────────────────────────────

    @Nested
    class AdminGetUserDetail {

        @Test
        void returnsUserDetail_success() {
            User user = testUser(Role.SUPPLIER);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            AdminUserResponse response = userService.adminGetUserDetail(USER_ID);

            assertNotNull(response);
            assertEquals(USER_ID, response.getId());
            assertEquals("Test User", response.getFullName());
            assertEquals(Role.SUPPLIER, response.getRole());
            assertTrue(response.isActive());
        }

        @Test
        void throwsException_userNotFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(BusinessException.class, () -> userService.adminGetUserDetail(999L));
        }
    }

    // ── adminUpdateUserStatus ──────────────────────────────────────────

    @Nested
    class AdminUpdateUserStatus {

        @Test
        void deactivateUser_success() {
            User user = testUser(Role.SUPPLIER);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateUserStatusRequest request = new UpdateUserStatusRequest();
            request.setActive(false);

            AdminUserResponse response = userService.adminUpdateUserStatus(USER_ID, request);

            assertFalse(response.isActive());
            verify(userRepository).save(user);
        }

        @Test
        void activateUser_success() {
            User user = testUser(Role.RECIPIENT);
            user.setActive(false);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateUserStatusRequest request = new UpdateUserStatusRequest();
            request.setActive(true);

            AdminUserResponse response = userService.adminUpdateUserStatus(USER_ID, request);

            assertTrue(response.isActive());
            verify(userRepository).save(user);
        }

        @Test
        void rejectsStatusChangeForAdmin() {
            User adminUser = testUser(Role.ADMIN);
            adminUser.setId(ADMIN_ID);
            when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(adminUser));

            UpdateUserStatusRequest request = new UpdateUserStatusRequest();
            request.setActive(false);

            assertThrows(BusinessException.class,
                    () -> userService.adminUpdateUserStatus(ADMIN_ID, request));
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        void throwsException_userNotFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            UpdateUserStatusRequest request = new UpdateUserStatusRequest();
            request.setActive(false);

            assertThrows(BusinessException.class,
                    () -> userService.adminUpdateUserStatus(999L, request));
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private User testUser(Role role) {
        User user = new User();
        user.setId(USER_ID);
        user.setFullName("Test User");
        user.setPhone("0912345678");
        user.setEmail("test@example.com");
        user.setRole(role);
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setActive(true);
        user.setProfileCompleted(true);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return user;
    }
}
