package com.datn.foodshare.service.payment.strategy;

import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.Payment;
import com.datn.foodshare.util.constant.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ZaloPayPaymentStrategyTest {

    private ZaloPayPaymentStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new ZaloPayPaymentStrategy();
    }

    @Test
    void processPayment_setsProcessingAndProvider() {
        Order order = Order.builder().id(1L).orderCode("ORD-1").build();
        Payment payment = Payment.builder().id(10L).amount(BigDecimal.valueOf(50000)).build();

        Payment result = strategy.processPayment(order, payment);

        assertSame(payment, result);
        assertEquals(TransactionStatus.PROCESSING, result.getPaymentStatus());
        assertEquals("ZALOPAY", result.getProvider());
        assertNotNull(result.getExternalTransactionId());
        assertTrue(result.getExternalTransactionId().startsWith("ZALOPAY-"));
    }

    @Test
    void createPayment_missingCredentials_throwsIllegalStateException() {
        Order order = Order.builder().id(1L).orderCode("ORD-1").build();
        Payment payment = Payment.builder().id(10L).amount(BigDecimal.valueOf(50000)).build();

        ReflectionTestUtils.setField(strategy, "appId", "");
        ReflectionTestUtils.setField(strategy, "key1", "");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> strategy.createPayment(order, payment));
        assertTrue(ex.getMessage().contains("Thiếu ZALOPAY_APP_ID/ZALOPAY_KEY1"));
    }

    @Test
    void processRefund_withoutCredentials_updatesPaymentLocally() {
        Payment payment = Payment.builder()
                .id(25L)
                .amount(BigDecimal.valueOf(75000))
                .paymentStatus(TransactionStatus.SUCCESS)
                .build();

        ReflectionTestUtils.setField(strategy, "appId", "");
        ReflectionTestUtils.setField(strategy, "key1", "");

        Payment result = strategy.processRefund(payment);

        assertSame(payment, result);
        assertEquals(TransactionStatus.REFUNDED, result.getPaymentStatus());
        assertNotNull(result.getRefundedAt());
        assertNotNull(result.getRefundTransactionId());
    }
}
