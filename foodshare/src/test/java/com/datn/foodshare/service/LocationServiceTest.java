package com.datn.foodshare.service;

import com.datn.foodshare.domain.response.LocationSuggestionResponse;
import com.datn.foodshare.util.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @Mock
    private GeoapifyClient geoapifyClient;

    @InjectMocks
    private LocationService locationService;

    @Test
    void autocompleteTrimsQueryAndReturnsProviderSuggestions() {
        LocationSuggestionResponse suggestion = suggestion();
        when(geoapifyClient.autocomplete("Đại Cồ Việt")).thenReturn(List.of(suggestion));

        List<LocationSuggestionResponse> result = locationService.autocomplete("  Đại Cồ Việt  ");

        assertEquals(List.of(suggestion), result);
        verify(geoapifyClient).autocomplete("Đại Cồ Việt");
    }

    @Test
    void autocompleteRejectsShortQueryWithoutCallingProvider() {
        assertThrows(BusinessException.class, () -> locationService.autocomplete("ab"));
        verifyNoInteractions(geoapifyClient);
    }

    @Test
    void reverseRejectsOutOfRangeCoordinatesWithoutCallingProvider() {
        assertThrows(BusinessException.class,
                () -> locationService.reverse(new BigDecimal("91"), new BigDecimal("105")));
        verifyNoInteractions(geoapifyClient);
    }

    @Test
    void reverseReturnsFirstProviderResult() {
        LocationSuggestionResponse suggestion = suggestion();
        BigDecimal latitude = new BigDecimal("21.0077");
        BigDecimal longitude = new BigDecimal("105.8431");
        when(geoapifyClient.reverse(latitude, longitude)).thenReturn(List.of(suggestion));

        assertEquals(suggestion, locationService.reverse(latitude, longitude));
    }

    @Test
    void reverseRejectsEmptyProviderResult() {
        BigDecimal latitude = new BigDecimal("21.0077");
        BigDecimal longitude = new BigDecimal("105.8431");
        when(geoapifyClient.reverse(latitude, longitude)).thenReturn(List.of());

        assertThrows(BusinessException.class, () -> locationService.reverse(latitude, longitude));
    }

    private LocationSuggestionResponse suggestion() {
        return new LocationSuggestionResponse(
                "1 Đại Cồ Việt, Hà Nội, Việt Nam",
                new BigDecimal("21.0077"),
                new BigDecimal("105.8431"),
                "place-id");
    }
}
