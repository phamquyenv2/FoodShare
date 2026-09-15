package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.entity.UserToken;
import com.datn.foodshare.domain.request.GoogleLoginRequest;
import com.datn.foodshare.domain.request.LoginRequest;
import com.datn.foodshare.domain.request.RegisterRequest;
import com.datn.foodshare.domain.response.GoogleUserInfo;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.repository.UserTokenRepository;
import com.datn.foodshare.security.GoogleTokenVerifier;
import com.datn.foodshare.security.JwtTokenProvider;
import com.datn.foodshare.util.constant.AuthProvider;
import com.datn.foodshare.util.constant.Role;
import com.datn.foodshare.util.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserTokenRepository userTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private GoogleTokenVerifier googleTokenVerifier;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                userTokenRepository,
                passwordEncoder,
                authenticationManager,
                jwtTokenProvider,
                googleTokenVerifier);
    }

    @Test
    void registerHashesPasswordAndIssuesTokens() {
        RegisterRequest request = registerRequest();
        allowVerifiedRegistration(request);
        when(passwordEncoder.encode("Password123")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });
        stubIssuedTokens();

        AuthService.AuthenticationResult result = authService.register(request);

        assertEquals("access-token", result.response().getAccessToken());
        assertEquals(Role.RECIPIENT, result.response().getRole());
        assertFalse(result.response().isProfileCompleted());
        assertEquals("refresh-token", result.refreshToken());
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("bcrypt-hash", userCaptor.getValue().getPasswordHash());
        assertEquals(AuthProvider.LOCAL, userCaptor.getValue().getAuthProvider());
        verify(userTokenRepository).save(any(UserToken.class));
    }

    @Test
    void registerRejectsDuplicatePhone() {
        RegisterRequest request = registerRequest();
        allowVerifiedRegistration(request);
        when(userRepository.existsByPhone("0901234567")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.register(request));

        assertEquals("Phone đã được sử dụng", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerRejectsAdminRole() {
        RegisterRequest request = registerRequest();
        request.setRole(Role.ADMIN);

        assertThrows(BusinessException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginAuthenticatesByPhoneAndIssuesTokens() {
        LoginRequest request = loginRequest();
        User user = localUser();
        when(userRepository.findByPhoneOrEmail("0901234567", "0901234567")).thenReturn(Optional.of(user));
        stubIssuedTokens();

        AuthService.AuthenticationResult result = authService.login(request);

        assertEquals(1L, result.response().getUserId());
        assertEquals("access-token", result.response().getAccessToken());
        verify(authenticationManager).authenticate(any());
    }

    @Test
    void loginRejectsWrongPassword() {
        LoginRequest request = loginRequest();
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Phone hoặc mật khẩu không chính xác"));

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
        verify(userRepository, never()).findByPhoneOrEmail(any(), any());
    }

    @Test
    void loginRejectsUnknownPhone() {
        LoginRequest request = loginRequest();
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Phone hoặc mật khẩu không chính xác"));

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }

    @Test
    void googleLoginCreatesGoogleUserAndIssuesFoodShareTokens() {
        GoogleLoginRequest request = new GoogleLoginRequest();
        request.setIdToken("google-id-token");
        request.setRole(Role.SUPPLIER);
        GoogleUserInfo info = GoogleUserInfo.builder()
                .googleId("google-subject")
                .email("User@Example.com")
                .emailVerified(true)
                .name("Google User")
                .picture("https://example.com/avatar.png")
                .build();
        when(googleTokenVerifier.verify("google-id-token")).thenReturn(info);
        when(userRepository.findByGoogleSubject("google-subject")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(2L);
            return user;
        });
        stubIssuedTokens();

        AuthService.AuthenticationResult result = authService.loginWithGoogle(request);

        assertEquals(AuthProvider.GOOGLE, result.response().getAuthProvider());
        assertEquals(Role.SUPPLIER, result.response().getRole());
        assertFalse(result.response().isProfileCompleted());
        assertEquals("access-token", result.response().getAccessToken());
    }

    @Test
    void googleLoginRejectsUnverifiedEmail() {
        GoogleLoginRequest request = new GoogleLoginRequest();
        request.setIdToken("google-id-token");
        when(googleTokenVerifier.verify("google-id-token")).thenReturn(GoogleUserInfo.builder()
                .googleId("subject")
                .email("user@example.com")
                .emailVerified(false)
                .build());

        assertThrows(BadCredentialsException.class, () -> authService.loginWithGoogle(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void refreshIssuesNewAccessTokenForStoredRefreshToken() {
        User user = localUser();
        UserToken storedToken = UserToken.builder()
                .id(10L)
                .user(user)
                .refreshToken("refresh-token")
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();
        when(jwtTokenProvider.validateRefreshToken("refresh-token")).thenReturn(true);
        when(userTokenRepository.findByRefreshTokenAndRevokedFalse(anyString()))
                .thenReturn(Optional.of(storedToken));
        when(jwtTokenProvider.getUserIdFromToken("refresh-token")).thenReturn(1L);
        when(jwtTokenProvider.createAccessToken(user)).thenReturn("new-access-token");

        when(jwtTokenProvider.createRefreshToken(user)).thenReturn("rotated-refresh-token");
        when(jwtTokenProvider.getExpirationFromToken("rotated-refresh-token"))
                .thenReturn(Date.from(Instant.now().plusSeconds(3600)));
        var result = authService.refreshAccessToken("refresh-token");

        assertEquals("new-access-token", result.response().getAccessToken());
        assertEquals("rotated-refresh-token", result.refreshToken());
        assertNotNull(storedToken.getLastActivatedAt());
        assertTrue(storedToken.isRevoked());
        verify(userTokenRepository).save(storedToken);
    }

    @Test
    void refreshRejectsInvalidToken() {
        when(jwtTokenProvider.validateRefreshToken("invalid")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.refreshAccessToken("invalid"));
        verify(userTokenRepository, never()).findByRefreshTokenAndRevokedFalse(any());
    }

    @Test
    void registerRejectsPhoneThatDoesNotMatchVerificationToken() {
        RegisterRequest request = registerRequest();
        when(jwtTokenProvider.validatePhoneRegistrationToken("phone-registration-token")).thenReturn(true);
        when(jwtTokenProvider.getPhoneFromToken("phone-registration-token")).thenReturn("0987654321");

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.register(request));

        assertEquals("Số điện thoại không khớp với phiên đã xác minh", exception.getMessage());
        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void register_duplicateNormalizedEmail_rejectsWithoutEncodingPassword() {
        RegisterRequest request = registerRequest();
        allowVerifiedRegistration(request);
        request.setEmail("  User@Example.com ");
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        assertThrows(BusinessException.class, () -> authService.register(request));

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_missingRole_rejectsBeforeRepositoryCalls() {
        RegisterRequest request = registerRequest();
        request.setRole(null);

        assertThrows(BusinessException.class, () -> authService.register(request));

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void login_inactiveAccount_rejectsWithoutIssuingTokens() {
        LoginRequest request = loginRequest();
        User user = localUser();
        user.setActive(false);
        when(userRepository.findByPhoneOrEmail("0901234567", "0901234567"))
                .thenReturn(Optional.of(user));

        assertThrows(BadCredentialsException.class, () -> authService.login(request));

        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void googleLogin_emailOwnedByLocalAccount_rejectsAccountLinking() {
        GoogleLoginRequest request = googleRequest();
        User localOwner = localUser();
        localOwner.setEmail("user@example.com");
        when(googleTokenVerifier.verify("google-id-token")).thenReturn(verifiedGoogleUser());
        when(userRepository.findByGoogleSubject("google-subject")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(localOwner));

        assertThrows(BusinessException.class, () -> authService.loginWithGoogle(request));

        verify(userRepository, never()).save(any());
    }

    @Test
    void refresh_expiredStoredToken_revokesItAndRejects() {
        UserToken storedToken = UserToken.builder()
                .user(localUser())
                .refreshToken("refresh-token")
                .expiresAt(Instant.now().minusSeconds(1))
                .revoked(false)
                .build();
        when(jwtTokenProvider.validateRefreshToken("refresh-token")).thenReturn(true);
        when(userTokenRepository.findByRefreshTokenAndRevokedFalse(anyString()))
                .thenReturn(Optional.of(storedToken));

        assertThrows(BadCredentialsException.class,
                () -> authService.refreshAccessToken("refresh-token"));

        assertTrue(storedToken.isRevoked());
        verify(userTokenRepository).save(storedToken);
        verify(jwtTokenProvider, never()).createAccessToken(any());
    }

    @Test
    void refresh_tokenUserDoesNotMatchStoredUser_rejectsWithoutUpdatingActivation() {
        UserToken storedToken = UserToken.builder()
                .user(localUser())
                .refreshToken("refresh-token")
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();
        when(jwtTokenProvider.validateRefreshToken("refresh-token")).thenReturn(true);
        when(userTokenRepository.findByRefreshTokenAndRevokedFalse(anyString()))
                .thenReturn(Optional.of(storedToken));
        when(jwtTokenProvider.getUserIdFromToken("refresh-token")).thenReturn(99L);

        assertThrows(BadCredentialsException.class,
                () -> authService.refreshAccessToken("refresh-token"));

        assertNull(storedToken.getLastActivatedAt());
        verify(userTokenRepository, never()).save(any());
    }

    @Test
    void logoutRevokesStoredRefreshToken() {
        UserToken storedToken = UserToken.builder().revoked(false).build();
        when(userTokenRepository.findByRefreshTokenAndRevokedFalse(anyString()))
                .thenReturn(Optional.of(storedToken));

        authService.revokeRefreshToken("refresh-token");

        assertTrue(storedToken.isRevoked());
        verify(userTokenRepository).save(storedToken);
    }

    private GoogleLoginRequest googleRequest() {
        GoogleLoginRequest request = new GoogleLoginRequest();
        request.setIdToken("google-id-token");
        request.setRole(Role.RECIPIENT);
        return request;
    }

    private GoogleUserInfo verifiedGoogleUser() {
        return GoogleUserInfo.builder()
                .googleId("google-subject")
                .email("User@Example.com")
                .emailVerified(true)
                .name("Google User")
                .build();
    }

    private void stubIssuedTokens() {
        when(jwtTokenProvider.createAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(any(User.class))).thenReturn("refresh-token");
        when(jwtTokenProvider.getExpirationFromToken("refresh-token"))
                .thenReturn(Date.from(Instant.now().plusSeconds(3600)));
    }

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setRegistrationToken("phone-registration-token");
        request.setPhone("0901234567");
        request.setPassword("Password123");
        request.setFullName("Test User");
        request.setRole(Role.RECIPIENT);
        return request;
    }

    private void allowVerifiedRegistration(RegisterRequest request) {
        when(jwtTokenProvider.validatePhoneRegistrationToken(request.getRegistrationToken())).thenReturn(true);
        when(jwtTokenProvider.getPhoneFromToken(request.getRegistrationToken())).thenReturn("0901234567");
    }

    private LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setIdentifier("0901234567");
        request.setPassword("Password123");
        return request;
    }

    private User localUser() {
        return User.builder()
                .id(1L)
                .phone("0901234567")
                .passwordHash("bcrypt-hash")
                .fullName("Test User")
                .role(Role.RECIPIENT)
                .authProvider(AuthProvider.LOCAL)
                .active(true)
                .build();
    }
}
