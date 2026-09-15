package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.util.constant.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    @Test
    void loadUserByUsername_activeSupplier_returnsExpectedPrincipalAndAuthority() {
        User user = user(Role.SUPPLIER, true);
        when(userRepository.findByPhoneOrEmail("supplier@example.com", "supplier@example.com"))
                .thenReturn(Optional.of(user));

        UserDetails result = userDetailsService.loadUserByUsername("supplier@example.com");

        assertEquals("supplier@example.com", result.getUsername());
        assertEquals("encoded-password", result.getPassword());
        assertTrue(result.isEnabled());
        assertEquals("ROLE_SUPPLIER", result.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void loadUserByUsername_inactiveUser_returnsDisabledPrincipal() {
        when(userRepository.findByPhoneOrEmail("0900000000", "0900000000"))
                .thenReturn(Optional.of(user(Role.RECIPIENT, false)));

        UserDetails result = userDetailsService.loadUserByUsername("0900000000");

        assertFalse(result.isEnabled());
        assertEquals("ROLE_RECIPIENT", result.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void loadUserByUsername_unknownIdentifier_throwsUsernameNotFound() {
        when(userRepository.findByPhoneOrEmail("unknown", "unknown")).thenReturn(Optional.empty());

        assertThrows(
                UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("unknown")
        );

        verify(userRepository).findByPhoneOrEmail("unknown", "unknown");
    }

    private static User user(Role role, boolean active) {
        User user = new User();
        user.setPasswordHash("encoded-password");
        user.setRole(role);
        user.setActive(active);
        return user;
    }
}
