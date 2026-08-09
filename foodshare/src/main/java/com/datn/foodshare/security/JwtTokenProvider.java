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
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpirationInMs;
    private final long refreshTokenExpirationInMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret:dGhpc19pc19hX3Zlcnlfc2VjdXJlX2tleV9mb3JfZm9vZHNoYXJlX2FwcGxpY2F0aW9uXzI1NmJpdHNfc2VjcmV0}") String jwtSecret,
            @Value("${app.jwt.access-token-expiration-in-seconds:86400}") long accessTokenExpirationInSeconds,
            @Value("${app.jwt.refresh-token-expiration-in-seconds:2592000}") long refreshTokenExpirationInSeconds) {
        
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtSecret);
        } catch (Exception e) {
            keyBytes = jwtSecret.getBytes();
        }
        if (keyBytes.length < 32) {
            // Pad or use standard key if too short for HS256/HS512
            byte[] paddedKey = new byte[32];
            System.arraycopy(keyBytes, 0, paddedKey, 0, Math.min(keyBytes.length, 32));
            keyBytes = paddedKey;
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
                .claim("phone", user.getPhone())
                .claim("email", user.getEmail())
                .claim("fullName", user.getFullName())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    public String createRefreshToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpirationInMs);

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("userId", user.getId())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("Expired JWT token: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("Unsupported JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
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
