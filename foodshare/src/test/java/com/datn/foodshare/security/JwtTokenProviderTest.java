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
    void consecutiveRefreshTokensForSameUserAreUnique() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60, 120);

        String first = provider.createRefreshToken(user());
        String second = provider.createRefreshToken(user());

        assertNotEquals(first, second);
    }

    @Test
    void phoneChallengeAndRegistrationTokensArePurposeBound() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60, 120);

        String challenge = provider.createPhoneOtpChallengeToken("0901234567", "pin-id");
        assertTrue(provider.validatePhoneOtpChallengeToken(challenge));
        assertFalse(provider.validatePhoneRegistrationToken(challenge));
        assertEquals("0901234567", provider.getPhoneFromToken(challenge));
        assertEquals("pin-id", provider.getPinIdFromChallengeToken(challenge));

        String registration = provider.createPhoneRegistrationToken("0901234567");
        assertTrue(provider.validatePhoneRegistrationToken(registration));
        assertFalse(provider.validateAccessToken(registration));
        assertEquals("0901234567", provider.getPhoneFromToken(registration));
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

    @Test
    void blankOrWeakSecretIsRejectedAtStartup() {
        assertThrows(IllegalStateException.class, () -> new JwtTokenProvider("", 60, 120));
        assertThrows(IllegalStateException.class, () -> new JwtTokenProvider("weak-secret", 60, 120));
        assertThrows(IllegalStateException.class,
                () -> new JwtTokenProvider("your_base64_encoded_256bit_secret_key", 60, 120));
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
