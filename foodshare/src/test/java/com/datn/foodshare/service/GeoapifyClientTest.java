package com.datn.foodshare.service;

import com.datn.foodshare.domain.response.LocationSuggestionResponse;
import com.datn.foodshare.util.error.ExternalServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeoapifyClientTest {

    @Test
    void autocomplete_missingApiKey_throwsExternalServiceException() {
        GeoapifyClient client = new GeoapifyClient("https://api.geoapify.com", "", 1000);

        ExternalServiceException ex = assertThrows(ExternalServiceException.class,
                () -> client.autocomplete("Hà Nội"));
        assertTrue(ex.getMessage().contains("chưa được cấu hình"));
    }

    @Test
    void reverse_missingApiKey_throwsExternalServiceException() {
        GeoapifyClient client = new GeoapifyClient("https://api.geoapify.com", null, 1000);

        ExternalServiceException ex = assertThrows(ExternalServiceException.class,
                () -> client.reverse(BigDecimal.valueOf(10.5), BigDecimal.valueOf(106.5)));
        assertTrue(ex.getMessage().contains("chưa được cấu hình"));
    }
}
