package com.datn.foodshare.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitFilterTest {

    @Test
    void authRequestsOverLimitReturn429() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(2, 2,
                Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC));
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 2; i++) {
            filter.doFilter(request("/api/auth/login"), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        filter.doFilter(request("/api/auth/login"), rejected, chain);

        assertEquals(429, rejected.getStatus());
        assertEquals("60", rejected.getHeader("Retry-After"));
        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unrelatedRequestsAreNotLimited() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, 1, Clock.systemUTC());
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request("/api/users/me"), new MockHttpServletResponse(), chain);
        filter.doFilter(request("/api/users/me"), new MockHttpServletResponse(), chain);

        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void businessDocumentUploadsUseUploadLimit() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(10, 1, Clock.systemUTC());
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request("/api/media/upload/business-document"),
                new MockHttpServletResponse(), chain);
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        filter.doFilter(request("/api/media/upload/business-document"), rejected, chain);

        assertEquals(429, rejected.getStatus());
        verify(chain, times(1)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void geocodingRequestsOverLimitReturn429() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(10, 10, 2,
                Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC));
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 2; i++) {
            filter.doFilter(request("GET", "/api/locations/autocomplete"), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        filter.doFilter(request("GET", "/api/locations/autocomplete"), rejected, chain);

        assertEquals(429, rejected.getStatus());
        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private MockHttpServletRequest request(String uri) {
        return request("POST", uri);
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
