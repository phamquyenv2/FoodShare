package com.datn.foodshare.domain.response;

import com.datn.foodshare.domain.entity.Payment;
import com.datn.foodshare.util.constant.PaymentMethod;
import com.datn.foodshare.util.constant.TransactionStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
public class PaymentResponse {
    private Long id;
    private Long orderId;
    private BigDecimal amount;
    private PaymentMethod method;
    private TransactionStatus status;
    private String externalTransactionId;
    private String provider;
    private Instant paidAt;

    public static PaymentResponse from(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrder().getId())
                .amount(payment.getAmount())
                .method(payment.getMethod())
                .status(payment.getPaymentStatus())
                .externalTransactionId(payment.getExternalTransactionId())
                .provider(payment.getProvider())
                .paidAt(payment.getPaidAt())
                .build();
    }
}
