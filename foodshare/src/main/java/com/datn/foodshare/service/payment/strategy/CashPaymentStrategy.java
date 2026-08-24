package com.datn.foodshare.service.payment.strategy;

import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.Payment;
import com.datn.foodshare.util.constant.TransactionStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class CashPaymentStrategy implements PaymentStrategy {
    @Override
    public Payment processPayment(Order order, Payment payment) {
        payment.setPaymentStatus(TransactionStatus.PENDING);
        payment.setProvider("CASH");
        return payment;
    }

    @Override
    public Payment processRefund(Payment payment) {
        payment.setPaymentStatus(TransactionStatus.REFUNDED);
        payment.setRefundedAt(Instant.now());
        return payment;
    }
}
