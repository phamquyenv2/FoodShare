package com.datn.foodshare.security;

import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.util.constant.AuthProvider;
import com.datn.foodshare.util.constant.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private static final String SECRET =
            "dGhpc19pc19hX3Rlc3Rfc2VjcmV0X2tleV90aGF0X2lzX2xvbmdfZW5vdWdoXzI1NmJpdA==";

    @Test
    void accessTokenContainsIdentityAndRoleAndCannotBeUsedAsRefreshToken() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60, 120);
        User user = user();

        String token = provider.createAccessToken(user);

        assertTrue(provider.validateAccessToken(token));
        assertFalse(provider.validateRefreshToken(token));
        assertEquals(7L, provider.getUserIdFromToken(token));
        assertEquals(Role.ADMIN, provider.getRoleFromToken(token));
        assertTrue(provider.getAuthentication(token).getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void refreshTokenCannotBeUsedAsAccessToken() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60, 120);

        String token = provider.createRefreshToken(user());

        assertTrue(provider.validateRefreshToken(token));
        assertFalse(provider.validateAccessToken(token));
    }

    @Test
    void expiredTokenIsRejected() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, -1, -1);

        assertFalse(provider.validateAccessToken(provider.createAccessToken(user())));
        assertFalse(provider.validateRefreshToken(provider.createRefreshToken(user())));
    }

    @Test
    void malformedTokenIsRejected() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60, 120);

        assertFalse(provider.validateAccessToken("not-a-jwt"));
        assertFalse(provider.validateRefreshToken("not-a-jwt"));
    }

    private User user() {
        return User.builder()
                .id(7L)
                .phone("0900000000")
                .fullName("Admin")
                .role(Role.ADMIN)
                .authProvider(AuthProvider.LOCAL)
                .active(true)
                .build();
    }
}
