package com.datn.foodshare.domain.request;

import com.datn.foodshare.util.constant.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentRequest {
    @NotNull(message = "Phương thức thanh toán không được để trống")
    private PaymentMethod method;
}
