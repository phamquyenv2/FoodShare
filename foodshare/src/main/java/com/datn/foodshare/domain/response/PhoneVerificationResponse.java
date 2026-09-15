package com.datn.foodshare.domain.response;

public record PhoneVerificationResponse(
        String registrationToken,
        String phone,
        long expiresInSeconds) {
}
