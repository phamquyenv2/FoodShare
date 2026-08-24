package com.datn.foodshare.service.payment.strategy;

import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.Payment;

public interface PaymentStrategy {
    Payment processPayment(Order order, Payment payment);
    Payment processRefund(Payment payment);
}
