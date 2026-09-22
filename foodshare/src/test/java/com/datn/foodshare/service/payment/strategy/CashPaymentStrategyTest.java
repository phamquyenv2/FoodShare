package com.datn.foodshare.service.payment.strategy;

import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.Payment;
import com.datn.foodshare.util.constant.TransactionStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CashPaymentStrategyTest {

    private final CashPaymentStrategy strategy = new CashPaymentStrategy();

    @Test
    void processPayment_setsPendingAndCashProvider() {
        Order order = Order.builder().id(1L).build();
        Payment payment = Payment.builder()
                .id(10L)
                .amount(BigDecimal.valueOf(50000))
                .build();

        Payment result = strategy.processPayment(order, payment);

        assertSame(payment, result);
        assertEquals(TransactionStatus.PENDING, result.getPaymentStatus());
        assertEquals("CASH", result.getProvider());
    }

    @Test
    void processRefund_setsRefundedAndTimestamp() {
        Payment payment = Payment.builder()
                .id(10L)
                .amount(BigDecimal.valueOf(50000))
                .paymentStatus(TransactionStatus.SUCCESS)
                .build();

        Payment result = strategy.processRefund(payment);

        assertSame(payment, result);
        assertEquals(TransactionStatus.REFUNDED, result.getPaymentStatus());
        assertNotNull(result.getRefundedAt());
    }
}
