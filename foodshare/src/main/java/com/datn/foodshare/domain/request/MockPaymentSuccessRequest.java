package com.datn.foodshare.domain.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MockPaymentSuccessRequest {
    @NotBlank(message = "successCode không được để trống")
    private String successCode;
}
