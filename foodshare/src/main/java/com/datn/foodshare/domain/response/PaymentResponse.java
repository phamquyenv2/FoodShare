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
    private String orderCode;
    private BigDecimal amount;
    private PaymentMethod method;
    private TransactionStatus status;
    private String externalTransactionId;
    private String refundTransactionId;
    private String provider;
    private Instant paidAt;
    private Instant refundedAt;
    private String paymentUrl;
    private String supplierName;

    public static PaymentResponse from(Payment payment) {
        String supplierName = null;
        if (payment.getOrder() != null && payment.getOrder().getBusinessProfile() != null) {
            supplierName = payment.getOrder().getBusinessProfile().getName();
        }
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrder() != null ? payment.getOrder().getId() : null)
                .orderCode(payment.getOrder() != null ? payment.getOrder().getOrderCode() : null)
                .amount(payment.getAmount())
                .method(payment.getMethod())
                .status(payment.getPaymentStatus())
                .externalTransactionId(payment.getExternalTransactionId())
                .refundTransactionId(payment.getRefundTransactionId())
                .provider(payment.getProvider())
                .paidAt(payment.getPaidAt())
                .refundedAt(payment.getRefundedAt())
                .paymentUrl(payment.getPaymentUrl())
                .supplierName(supplierName)
                .build();
    }
}
