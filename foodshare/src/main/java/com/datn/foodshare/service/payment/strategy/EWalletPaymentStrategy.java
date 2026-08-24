package com.datn.foodshare.service.payment.strategy;

import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.Payment;
import com.datn.foodshare.util.constant.TransactionStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class EWalletPaymentStrategy implements PaymentStrategy {
    @Override
    public Payment processPayment(Order order, Payment payment) {
        payment.setPaymentStatus(TransactionStatus.PROCESSING);
        payment.setProvider("EWALLET");
        payment.setExternalTransactionId("EXT-" + UUID.randomUUID().toString());
        return payment;
    }

    @Override
    public Payment processRefund(Payment payment) {
        payment.setPaymentStatus(TransactionStatus.REFUNDED);
        payment.setRefundTransactionId("REF-" + UUID.randomUUID().toString());
        payment.setRefundedAt(Instant.now());
        return payment;
    }
}
