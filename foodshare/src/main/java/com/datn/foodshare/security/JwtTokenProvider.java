package com.datn.foodshare.security;

import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.util.constant.Role;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";
    private static final String PHONE_OTP_CHALLENGE_TYPE = "phone_otp_challenge";
    private static final String PHONE_REGISTRATION_TYPE = "phone_registration";
    private static final long PHONE_OTP_CHALLENGE_EXPIRATION_MS = 15 * 60 * 1000L;
    private static final long PHONE_REGISTRATION_EXPIRATION_MS = 10 * 60 * 1000L;

    private final SecretKey secretKey;
    private final long accessTokenExpirationInMs;
    private final long refreshTokenExpirationInMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret:}") String jwtSecret,
            @Value("${app.jwt.access-token-expiration-in-seconds:900}") long accessTokenExpirationInSeconds,
            @Value("${app.jwt.refresh-token-expiration-in-seconds:2592000}") long refreshTokenExpirationInSeconds) {
        
        if (jwtSecret == null || jwtSecret.isBlank()
                || jwtSecret.contains("CHANGE_ME") || jwtSecret.contains("your_")) {
            throw new IllegalStateException("JWT_SECRET must be explicitly configured");
        }
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtSecret);
        } catch (Exception e) {
            keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 256 bits of key material");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpirationInMs = accessTokenExpirationInSeconds * 1000;
        this.refreshTokenExpirationInMs = refreshTokenExpirationInSeconds * 1000;
    }

    public String createAccessToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpirationInMs);

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("userId", user.getId())
                .claim("role", user.getRole().name())
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    public String createRefreshToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpirationInMs);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(user.getId()))
                .claim("userId", user.getId())
                .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    public boolean validateAccessToken(String token) {
        return validateToken(token, ACCESS_TOKEN_TYPE);
    }

    public boolean validateRefreshToken(String token) {
        return validateToken(token, REFRESH_TOKEN_TYPE);
    }

    public String createPhoneOtpChallengeToken(String phone, String pinId) {
        return createPurposeToken(phone, PHONE_OTP_CHALLENGE_TYPE,
                PHONE_OTP_CHALLENGE_EXPIRATION_MS, "pinId", pinId);
    }

    public boolean validatePhoneOtpChallengeToken(String token) {
        return validateToken(token, PHONE_OTP_CHALLENGE_TYPE);
    }

    public String createPhoneRegistrationToken(String phone) {
        return createPurposeToken(phone, PHONE_REGISTRATION_TYPE,
                PHONE_REGISTRATION_EXPIRATION_MS, null, null);
    }

    public boolean validatePhoneRegistrationToken(String token) {
        return validateToken(token, PHONE_REGISTRATION_TYPE);
    }

    public String getPhoneFromToken(String token) {
        return getClaims(token).getSubject();
    }

    public String getPinIdFromChallengeToken(String token) {
        return getClaims(token).get("pinId", String.class);
    }

    private String createPurposeToken(String subject, String type, long expirationMs,
                                      String claimName, String claimValue) {
        Date now = new Date();
        JwtBuilder builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(subject)
                .claim(TOKEN_TYPE_CLAIM, type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs));
        if (claimName != null) {
            builder.claim(claimName, claimValue);
        }
        return builder.signWith(secretKey).compact();
    }

    private boolean validateToken(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return expectedType.equals(claims.get(TOKEN_TYPE_CLAIM, String.class));
        } catch (SecurityException | MalformedJwtException e) {
            log.debug("Chữ ký JWT không hợp lệ");
        } catch (ExpiredJwtException e) {
            log.debug("Token JWT đã hết hạn");
        } catch (UnsupportedJwtException e) {
            log.debug("Token JWT không được hỗ trợ");
        } catch (IllegalArgumentException e) {
            log.debug("Chuỗi JWT claims trống");
        }
        return false;
    }

    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = getClaims(token);
        Object userId = claims.get("userId");
        if (userId instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(claims.getSubject());
    }

    public Date getExpirationFromToken(String token) {
        return getClaims(token).getExpiration();
    }

    public Role getRoleFromToken(String token) {
        Claims claims = getClaims(token);
        String roleStr = claims.get("role", String.class);
        return roleStr != null ? Role.valueOf(roleStr) : null;
    }

    public Authentication getAuthentication(String token) {
        Claims claims = getClaims(token);
        String role = claims.get("role", String.class);
        String principal = claims.getSubject();

        List<GrantedAuthority> authorities = role != null
                ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
                : Collections.emptyList();

        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }
}
