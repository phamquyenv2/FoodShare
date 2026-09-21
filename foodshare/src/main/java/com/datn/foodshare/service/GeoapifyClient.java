package com.datn.foodshare.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.datn.foodshare.domain.response.LocationSuggestionResponse;
import com.datn.foodshare.util.error.ExternalServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

@Slf4j
@Component
public class GeoapifyClient {

    private final RestClient restClient;
    private final String apiKey;

    public GeoapifyClient(
            @Value("${geoapify.base-url:https://api.geoapify.com}") String baseUrl,
            @Value("${geoapify.api-key:}") String apiKey,
            @Value("${geoapify.timeout-ms:5000}") int timeoutMs) {
        Duration timeout = Duration.ofMillis(timeoutMs);
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.apiKey = apiKey;
    }

    public List<LocationSuggestionResponse> autocomplete(String text) {
        GeoapifyResponse response = execute(builder -> builder
                .path("/v1/geocode/autocomplete")
                .queryParam("text", text)
                .queryParam("filter", "countrycode:vn")
                .queryParam("lang", "vi")
                .queryParam("limit", 5)
                .queryParam("format", "json")
                .queryParam("apiKey", apiKey)
                .build());
        return toSuggestions(response);
    }

    public List<LocationSuggestionResponse> reverse(BigDecimal latitude, BigDecimal longitude) {
        GeoapifyResponse response = execute(builder -> builder
                .path("/v1/geocode/reverse")
                .queryParam("lat", latitude)
                .queryParam("lon", longitude)
                .queryParam("lang", "vi")
                .queryParam("limit", 1)
                .queryParam("format", "json")
                .queryParam("apiKey", apiKey)
                .build());
        return toSuggestions(response);
    }

    private GeoapifyResponse execute(java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI> uriFunction) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ExternalServiceException("Dịch vụ định vị chưa được cấu hình");
        }
        try {
            return restClient.get()
                    .uri(uriFunction)
                    .retrieve()
                    .body(GeoapifyResponse.class);
        } catch (RestClientException ex) {
            log.warn("Geoapify request failed: {}", ex.getClass().getSimpleName());
            throw new ExternalServiceException("Không thể tra cứu vị trí lúc này");
        }
    }

    private List<LocationSuggestionResponse> toSuggestions(GeoapifyResponse response) {
        if (response == null || response.results() == null) {
            return List.of();
        }
        return response.results().stream()
                .filter(result -> result.formatted() != null && !result.formatted().isBlank())
                .filter(result -> result.latitude() != null && result.longitude() != null)
                .map(result -> new LocationSuggestionResponse(
                        result.formatted(),
                        result.latitude(),
                        result.longitude(),
                        result.placeId()))
                .toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeoapifyResponse(List<GeoapifyResult> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeoapifyResult(
            String formatted,
            @JsonProperty("lat") BigDecimal latitude,
            @JsonProperty("lon") BigDecimal longitude,
            @JsonProperty("place_id") String placeId
    ) {
    }
}
