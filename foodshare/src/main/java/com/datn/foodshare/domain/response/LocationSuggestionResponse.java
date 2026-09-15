package com.datn.foodshare.domain.response;

import java.math.BigDecimal;

public record LocationSuggestionResponse(
        String formattedAddress,
        BigDecimal latitude,
        BigDecimal longitude,
        String placeId
) {
}
