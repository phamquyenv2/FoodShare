package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.entity.UserToken;
import com.datn.foodshare.domain.request.GoogleLoginRequest;
import com.datn.foodshare.domain.request.LoginRequest;
import com.datn.foodshare.domain.request.RegisterRequest;
import com.datn.foodshare.domain.response.AuthResponse;
import com.datn.foodshare.domain.response.GoogleUserInfo;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.repository.UserTokenRepository;
import com.datn.foodshare.security.GoogleTokenVerifier;
import com.datn.foodshare.security.JwtTokenProvider;
import com.datn.foodshare.util.constant.AuthProvider;
import com.datn.foodshare.util.constant.Role;
import com.datn.foodshare.util.PhoneNumberUtil;
import com.datn.foodshare.util.error.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final UserTokenRepository userTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final GoogleTokenVerifier googleTokenVerifier;

    @Autowired(required = false)
    private CloudinaryService cloudinaryService;

    @Transactional
    public AuthenticationResult register(RegisterRequest request) {
        validateSelfRegistrationRole(request.getRole());
        String phone = PhoneNumberUtil.normalizeVietnamese(request.getPhone());
        String email = normalizeEmail(request.getEmail());

        if (!jwtTokenProvider.validatePhoneRegistrationToken(request.getRegistrationToken())) {
            throw new BusinessException("Số điện thoại chưa được xác minh hoặc phiên xác minh đã hết hạn");
        }
        String verifiedPhone = jwtTokenProvider.getPhoneFromToken(request.getRegistrationToken());
        if (!phone.equals(verifiedPhone)) {
            throw new BusinessException("Số điện thoại không khớp với phiên đã xác minh");
        }

        if (userRepository.existsByPhone(phone)) {
            throw new BusinessException("Phone đã được sử dụng");
        }
        if (email != null && userRepository.existsByEmail(email)) {
            throw new BusinessException("Email đã được sử dụng");
        }

        User user = User.builder()
                .phone(phone)
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .role(request.getRole())
                .authProvider(AuthProvider.LOCAL)
                .active(true)
                .build();

        return issueTokens(userRepository.save(user));
    }

    @Transactional
    public AuthenticationResult login(LoginRequest request) {
        String identifier = request.getIdentifier().trim();
        authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(identifier, request.getPassword()));

        User user = userRepository.findByPhoneOrEmail(identifier, identifier)
                .orElseThrow(() -> new BadCredentialsException("Tài khoản hoặc mật khẩu không chính xác"));
        if (!user.isActive()) {
            throw new BadCredentialsException("Tài khoản không hoạt động");
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthenticationResult loginWithGoogle(GoogleLoginRequest request) {
        GoogleUserInfo googleUser = googleTokenVerifier.verify(request.getIdToken());
        if (!Boolean.TRUE.equals(googleUser.getEmailVerified()) || googleUser.getEmail() == null) {
            throw new BadCredentialsException("Tài khoản Google chưa xác minh email");
        }

        String email = normalizeEmail(googleUser.getEmail());
        User user = userRepository.findByGoogleSubject(googleUser.getGoogleId()).orElse(null);

        if (user == null) {
            User emailOwner = userRepository.findByEmail(email).orElse(null);
            if (emailOwner != null) {
                if (emailOwner.getAuthProvider() != AuthProvider.GOOGLE) {
                    throw new BusinessException("Email đã thuộc tài khoản đăng nhập bằng phone");
                }
                emailOwner.setGoogleSubject(googleUser.getGoogleId());
                user = userRepository.save(emailOwner);
            } else {
                validateSelfRegistrationRole(request.getRole());
                user = User.builder()
                        .email(email)
                        .googleSubject(googleUser.getGoogleId())
                        .fullName(resolveGoogleName(googleUser))
                        .avatarUrl(uploadGoogleAvatar(googleUser.getPicture()))
                        .role(request.getRole())
                        .authProvider(AuthProvider.GOOGLE)
                        .active(true)
                        .build();
                user = userRepository.save(user);
            }
        }

        if (cloudinaryService != null && (user.getAvatarUrl() == null
                || user.getAvatarUrl().contains("googleusercontent.com"))) {
            String migratedAvatar = uploadGoogleAvatar(user.getAvatarUrl() == null
                    ? googleUser.getPicture() : user.getAvatarUrl());
            if (migratedAvatar != null) {
                user.setAvatarUrl(migratedAvatar);
                user = userRepository.save(user);
            }
        }

        if (!user.isActive()) {
            throw new BadCredentialsException("Tài khoản không hoạt động");
        }
        return issueTokens(user);
    }

    private String uploadGoogleAvatar(String pictureUrl) {
        if (cloudinaryService == null || pictureUrl == null || pictureUrl.isBlank()) return null;
        try {
            java.net.URI uri = java.net.URI.create(pictureUrl);
            String host = uri.getHost();
            if (host == null || !(host.equals("googleusercontent.com") || host.endsWith(".googleusercontent.com"))) return null;
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(false);
            if (connection.getResponseCode() != 200) return null;
            String contentType = connection.getContentType();
            if (contentType != null && contentType.contains(";")) contentType = contentType.substring(0, contentType.indexOf(';'));
            byte[] bytes;
            try (java.io.InputStream input = connection.getInputStream()) {
                bytes = input.readNBytes(10 * 1024 * 1024 + 1);
            }
            return cloudinaryService.uploadUserAvatar(bytes, contentType);
        } catch (Exception e) {
            log.warn("Không thể đồng bộ avatar Google về Cloudinary: {}", e.getMessage());
            return null;
        }
    }

    @Transactional
    public AuthenticationResult refreshAccessToken(String refreshToken) {
        if (refreshToken == null || !jwtTokenProvider.validateRefreshToken(refreshToken)) {
            throw new BadCredentialsException("Refresh token không hợp lệ hoặc đã hết hạn");
        }

        UserToken storedToken = userTokenRepository.findByRefreshTokenAndRevokedFalse(hashToken(refreshToken))
                .orElseThrow(() -> new BadCredentialsException("Refresh token không hợp lệ hoặc đã bị thu hồi"));

        if (!storedToken.getExpiresAt().isAfter(Instant.now()) || !storedToken.getUser().isActive()) {
            storedToken.setRevoked(true);
            userTokenRepository.save(storedToken);
            throw new BadCredentialsException("Refresh token không hợp lệ hoặc đã hết hạn");
        }

        Long tokenUserId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        if (!storedToken.getUser().getId().equals(tokenUserId)) {
            throw new BadCredentialsException("Refresh token không hợp lệ");
        }

        storedToken.setLastActivatedAt(Instant.now());
        storedToken.setRevoked(true);
        userTokenRepository.save(storedToken);
        return issueTokens(storedToken.getUser());
    }

    @Transactional
    public void revokeRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return;
        userTokenRepository.findByRefreshTokenAndRevokedFalse(hashToken(refreshToken)).ifPresent(token -> {
            token.setRevoked(true);
            userTokenRepository.save(token);
        });
    }

    private AuthenticationResult issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user);
        String refreshToken = jwtTokenProvider.createRefreshToken(user);
        UserToken storedToken = UserToken.builder()
                .user(user)
                .refreshToken(hashToken(refreshToken))
                .expiresAt(jwtTokenProvider.getExpirationFromToken(refreshToken).toInstant())
                .build();
        userTokenRepository.save(storedToken);
        return new AuthenticationResult(AuthResponse.from(user, accessToken), refreshToken);
    }

    private void validateSelfRegistrationRole(Role role) {
        if (role == null) {
            throw new BusinessException("Role là bắt buộc khi tạo tài khoản mới");
        }
        if (role == Role.ADMIN) {
            throw new BusinessException("Không thể tự đăng ký tài khoản ADMIN");
        }
    }

    private String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveGoogleName(GoogleUserInfo googleUser) {
        return googleUser.getName() == null || googleUser.getName().isBlank()
                ? googleUser.getEmail()
                : googleUser.getName().trim();
    }

    private String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record AuthenticationResult(AuthResponse response, String refreshToken) {
    }
}
