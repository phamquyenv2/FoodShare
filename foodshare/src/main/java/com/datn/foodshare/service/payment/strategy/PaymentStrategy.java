package com.datn.foodshare.service.payment.strategy;

import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.Payment;
import java.util.Map;

public interface PaymentStrategy {
    Payment processPayment(Order order, Payment payment);
    Payment processRefund(Payment payment);

    default Payment createPayment(Order order, Payment payment) { return processPayment(order, payment); }
    default void processCallback(Map<String, String> payload) { throw new UnsupportedOperationException("Callback not supported"); }
    default Payment queryPaymentStatus(Payment payment) { return payment; }
    default Payment queryRefundStatus(Payment payment) { return payment; }
}
