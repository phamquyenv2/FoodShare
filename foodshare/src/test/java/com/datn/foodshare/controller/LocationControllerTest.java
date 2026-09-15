package com.datn.foodshare.controller;

import com.datn.foodshare.config.SecurityConfiguration;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.response.LocationSuggestionResponse;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.security.CustomAccessDeniedHandler;
import com.datn.foodshare.security.CustomAuthenticationEntryPoint;
import com.datn.foodshare.security.JwtAuthenticationFilter;
import com.datn.foodshare.security.JwtTokenProvider;
import com.datn.foodshare.security.RateLimitFilter;
import com.datn.foodshare.service.CustomUserDetailsService;
import com.datn.foodshare.service.LocationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LocationController.class)
@Import({
        SecurityConfiguration.class,
        JwtAuthenticationFilter.class,
        RateLimitFilter.class,
        CustomAuthenticationEntryPoint.class,
        CustomAccessDeniedHandler.class
})
class LocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LocationService locationService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void anonymousCannotUseGeocodingProxy() throws Exception {
        mockMvc.perform(get("/api/locations/autocomplete").param("text", "Hà Nội"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanAutocompleteAddress() throws Exception {
        stubAccessToken();
        LocationSuggestionResponse suggestion = suggestion();
        when(locationService.autocomplete("Đại Cồ Việt")).thenReturn(List.of(suggestion));

        mockMvc.perform(get("/api/locations/autocomplete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                        .param("text", "Đại Cồ Việt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].formattedAddress").value(suggestion.formattedAddress()))
                .andExpect(jsonPath("$.data[0].latitude").value(21.0077));
    }

    @Test
    void authenticatedUserCanReverseGeocode() throws Exception {
        stubAccessToken();
        LocationSuggestionResponse suggestion = suggestion();
        when(locationService.reverse(new BigDecimal("21.0077"), new BigDecimal("105.8431")))
                .thenReturn(suggestion);

        mockMvc.perform(get("/api/locations/reverse")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                        .param("latitude", "21.0077")
                        .param("longitude", "105.8431"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.formattedAddress").value(suggestion.formattedAddress()));
    }

    private void stubAccessToken() {
        when(jwtTokenProvider.validateAccessToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken("valid-token")).thenReturn(1L);
        User activeUser = new User();
        activeUser.setId(1L);
        activeUser.setActive(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(jwtTokenProvider.getAuthentication("valid-token")).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated(
                        "1",
                        "valid-token",
                        List.of(new SimpleGrantedAuthority("ROLE_RECIPIENT"))));
    }

    private LocationSuggestionResponse suggestion() {
        return new LocationSuggestionResponse(
                "1 Đại Cồ Việt, Hà Nội, Việt Nam",
                new BigDecimal("21.0077"),
                new BigDecimal("105.8431"),
                "place-id");
    }
}
