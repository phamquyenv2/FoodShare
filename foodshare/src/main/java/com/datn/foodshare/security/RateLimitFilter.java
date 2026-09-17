package com.datn.foodshare.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final int authLimit;
    private final int uploadLimit;
    private final int geocodingLimit;
    private final long windowMillis;
    private final Clock clock;

    @Autowired
    public RateLimitFilter(
            @Value("${app.security.rate-limit.auth-per-minute:10}") int authLimit,
            @Value("${app.security.rate-limit.upload-per-minute:20}") int uploadLimit,
            @Value("${app.security.rate-limit.geocoding-per-minute:30}") int geocodingLimit) {
        this(authLimit, uploadLimit, geocodingLimit, Clock.systemUTC());
    }

    RateLimitFilter(int authLimit, int uploadLimit, Clock clock) {
        this(authLimit, uploadLimit, 30, clock);
    }

    RateLimitFilter(int authLimit, int uploadLimit, int geocodingLimit, Clock clock) {
        if (authLimit < 1 || uploadLimit < 1 || geocodingLimit < 1) {
            throw new IllegalArgumentException("Rate limits must be positive");
        }
        this.authLimit = authLimit;
        this.uploadLimit = uploadLimit;
        this.geocodingLimit = geocodingLimit;
        this.windowMillis = 60_000L;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        LimitRule rule = ruleFor(request);
        if (rule != null && !acquire(clientKey(request) + ':' + rule.group(), rule.limit())) {
            response.setStatus(429);
            response.setCharacterEncoding("UTF-8");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", "60");
            response.getWriter().write("{\"statusCode\":429,\"error\":\"Too Many Requests\",\"message\":\"Quá nhiều yêu cầu, vui lòng thử lại sau\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private LimitRule ruleFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("GET".equals(request.getMethod()) && path.startsWith("/api/locations/")) {
            return new LimitRule("geocoding", geocodingLimit);
        }
        if (!"POST".equals(request.getMethod())) return null;
        if (path.equals("/api/auth/login") || path.equals("/api/auth/register")
                || path.equals("/api/auth/google") || path.equals("/api/auth/refresh")
                || path.equals("/api/auth/phone-otp/send")
                || path.equals("/api/auth/phone-otp/verify")) {
            return new LimitRule("auth", authLimit);
        }
        if (path.startsWith("/api/media/upload")) return new LimitRule("upload", uploadLimit);
        return null;
    }

    private String clientKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return "user:" + authentication.getName();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private boolean acquire(String key, int limit) {
        long now = clock.millis();
        WindowCounter updated = counters.compute(key, (ignored, current) -> {
            if (current == null || now - current.windowStart >= windowMillis) {
                return new WindowCounter(now, 1);
            }
            return new WindowCounter(current.windowStart, current.count + 1);
        });
        if (counters.size() > 10_000) {
            counters.entrySet().removeIf(entry -> now - entry.getValue().windowStart >= windowMillis);
        }
        return updated.count <= limit;
    }

    private record LimitRule(String group, int limit) {}
    private record WindowCounter(long windowStart, int count) {}
}
