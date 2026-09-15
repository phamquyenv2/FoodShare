package com.datn.foodshare.domain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyPhoneOtpRequest(
        @NotBlank(message = "Phiên xác minh OTP không được để trống")
        String challengeToken,
        @NotBlank(message = "OTP không được để trống")
        @Pattern(regexp = "^[0-9]{4}$", message = "OTP phải gồm đúng 4 chữ số")
        String otp) {
}
