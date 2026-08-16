package com.datn.foodshare.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoogleTokenVerifierTest {

    @Test
    void verifiedGoogleTokenReturnsIdentity() throws Exception {
        GoogleIdTokenVerifier delegate = mock(GoogleIdTokenVerifier.class);
        GoogleIdToken idToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("google-subject");
        payload.setEmail("user@example.com");
        payload.setEmailVerified(true);
        payload.set("name", "Google User");
        payload.set("picture", "https://example.com/avatar.png");
        when(delegate.verify("valid-token")).thenReturn(idToken);
        when(idToken.getPayload()).thenReturn(payload);

        var result = new GoogleTokenVerifier(delegate).verify("valid-token");

        assertEquals("google-subject", result.getGoogleId());
        assertEquals("user@example.com", result.getEmail());
        assertTrue(result.getEmailVerified());
    }

    @Test
    void invalidGoogleTokenIsRejected() throws Exception {
        GoogleIdTokenVerifier delegate = mock(GoogleIdTokenVerifier.class);
        when(delegate.verify("invalid-token")).thenReturn(null);

        assertThrows(BadCredentialsException.class,
                () -> new GoogleTokenVerifier(delegate).verify("invalid-token"));
    }

    @Test
    void unverifiedGoogleEmailIsRejected() throws Exception {
        GoogleIdTokenVerifier delegate = mock(GoogleIdTokenVerifier.class);
        GoogleIdToken idToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("google-subject");
        payload.setEmail("user@example.com");
        payload.setEmailVerified(false);
        when(delegate.verify("unverified-token")).thenReturn(idToken);
        when(idToken.getPayload()).thenReturn(payload);

        assertThrows(BadCredentialsException.class,
                () -> new GoogleTokenVerifier(delegate).verify("unverified-token"));
    }
}
