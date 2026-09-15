package com.datn.foodshare.domain.response;

public record PhoneOtpChallengeResponse(
        String challengeToken,
        String maskedPhone,
        long expiresInSeconds) {
}
