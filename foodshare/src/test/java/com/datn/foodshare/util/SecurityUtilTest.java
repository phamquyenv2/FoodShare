package com.datn.foodshare.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SecurityUtilTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void whenNoAuthentication_returnsEmpty() {
        SecurityContextHolder.clearContext();

        assertTrue(SecurityUtil.getCurrentUserLogin().isEmpty());
        assertTrue(SecurityUtil.getCurrentUserId().isEmpty());
        assertTrue(SecurityUtil.getAuthentication().isEmpty());
        assertTrue(SecurityUtil.getCurrentUserRole().isEmpty());
        assertFalse(SecurityUtil.hasRole("ADMIN"));
    }

    @Test
    void withUserDetailsPrincipal_returnsCorrectValues() {
        UserDetails userDetails = User.builder()
                .username("42")
                .password("password")
                .roles("ADMIN")
                .build();

        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        assertEquals(Optional.of("42"), SecurityUtil.getCurrentUserLogin());
        assertEquals(Optional.of(42L), SecurityUtil.getCurrentUserId());
        assertTrue(SecurityUtil.getAuthentication().isPresent());
        assertEquals(Optional.of("ROLE_ADMIN"), SecurityUtil.getCurrentUserRole());
        assertTrue(SecurityUtil.hasRole("ADMIN"));
        assertTrue(SecurityUtil.hasRole("ROLE_ADMIN"));
        assertFalse(SecurityUtil.hasRole("USER"));
    }

    @Test
    void withStringPrincipal_returnsCorrectValues() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "100",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        assertEquals(Optional.of("100"), SecurityUtil.getCurrentUserLogin());
        assertEquals(Optional.of(100L), SecurityUtil.getCurrentUserId());
        assertTrue(SecurityUtil.hasRole("USER"));
    }

    @Test
    void withNonNumericPrincipal_getCurrentUserIdReturnsEmpty() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "not-a-number",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        assertEquals(Optional.of("not-a-number"), SecurityUtil.getCurrentUserLogin());
        assertTrue(SecurityUtil.getCurrentUserId().isEmpty());
    }

    @Test
    void withUnknownPrincipalType_extractPrincipalReturnsNull() {
        Object customPrincipal = new Object();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                customPrincipal,
                null,
                List.of()
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        assertTrue(SecurityUtil.getCurrentUserLogin().isEmpty());
        assertTrue(SecurityUtil.getCurrentUserId().isEmpty());
    }
}
