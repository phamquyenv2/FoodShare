package com.datn.foodshare.service;

import com.datn.foodshare.domain.response.LocationSuggestionResponse;
import com.datn.foodshare.util.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LocationService {

    private static final int MIN_QUERY_LENGTH = 3;
    private static final int MAX_QUERY_LENGTH = 200;
    private static final BigDecimal MIN_LATITUDE = new BigDecimal("-90");
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");
    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("-180");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");

    private final GeoapifyClient geoapifyClient;

    public List<LocationSuggestionResponse> autocomplete(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.length() < MIN_QUERY_LENGTH) {
            throw new BusinessException("Địa chỉ tìm kiếm phải có ít nhất 3 ký tự");
        }
        if (normalized.length() > MAX_QUERY_LENGTH) {
            throw new BusinessException("Địa chỉ tìm kiếm không được vượt quá 200 ký tự");
        }
        return geoapifyClient.autocomplete(normalized);
    }

    public LocationSuggestionResponse reverse(BigDecimal latitude, BigDecimal longitude) {
        validateCoordinate(latitude, MIN_LATITUDE, MAX_LATITUDE, "Latitude");
        validateCoordinate(longitude, MIN_LONGITUDE, MAX_LONGITUDE, "Longitude");
        return geoapifyClient.reverse(latitude, longitude).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException("Không tìm thấy địa chỉ cho vị trí hiện tại"));
    }

    private void validateCoordinate(BigDecimal value, BigDecimal min, BigDecimal max, String name) {
        if (value == null || value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new BusinessException(name + " không hợp lệ");
        }
    }
}
