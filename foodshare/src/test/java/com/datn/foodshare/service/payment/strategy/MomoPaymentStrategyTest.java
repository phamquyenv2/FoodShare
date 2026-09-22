package com.datn.foodshare.service.payment.strategy;

import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.Payment;
import com.datn.foodshare.util.constant.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class MomoPaymentStrategyTest {

    private MomoPaymentStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new MomoPaymentStrategy();
    }

    @Test
    void processPayment_setsProcessingAndProvider() {
        Order order = Order.builder().id(1L).orderCode("ORD-1").build();
        Payment payment = Payment.builder().id(10L).amount(BigDecimal.valueOf(50000)).build();

        Payment result = strategy.processPayment(order, payment);

        assertSame(payment, result);
        assertEquals(TransactionStatus.PROCESSING, result.getPaymentStatus());
        assertEquals("MOMO", result.getProvider());
        assertNotNull(result.getExternalTransactionId());
        assertTrue(result.getExternalTransactionId().startsWith("MOMO-"));
    }

    @Test
    void createPayment_missingCredentials_throwsIllegalStateException() {
        Order order = Order.builder().id(1L).orderCode("ORD-1").build();
        Payment payment = Payment.builder().id(10L).amount(BigDecimal.valueOf(50000)).build();

        ReflectionTestUtils.setField(strategy, "partnerCode", "");
        ReflectionTestUtils.setField(strategy, "accessKey", "");
        ReflectionTestUtils.setField(strategy, "secretKey", "");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> strategy.createPayment(order, payment));
        assertTrue(ex.getMessage().contains("Thiếu cấu hình"));
    }

    @Test
    void processRefund_withoutCredentials_updatesPaymentLocally() {
        Payment payment = Payment.builder()
                .id(20L)
                .amount(BigDecimal.valueOf(100000))
                .paymentStatus(TransactionStatus.SUCCESS)
                .build();

        ReflectionTestUtils.setField(strategy, "partnerCode", "");
        ReflectionTestUtils.setField(strategy, "accessKey", "");
        ReflectionTestUtils.setField(strategy, "secretKey", "");

        Payment result = strategy.processRefund(payment);

        assertSame(payment, result);
        assertEquals(TransactionStatus.REFUNDED, result.getPaymentStatus());
        assertNotNull(result.getRefundedAt());
        assertNotNull(result.getRefundTransactionId());
        assertTrue(result.getRefundTransactionId().startsWith("FS-REF-20-"));
    }

    @Test
    void normalizeUrl_handlesMarkdownFormatAndPlainUrl() {
        String markdownUrl = "[link](https://example.com/callback)";
        String plainUrl = "https://example.com/callback";

        String normalizedMarkdown = ReflectionTestUtils.invokeMethod(strategy, "normalizeUrl", markdownUrl);
        String normalizedPlain = ReflectionTestUtils.invokeMethod(strategy, "normalizeUrl", plainUrl);
        String normalizedNull = ReflectionTestUtils.invokeMethod(strategy, "normalizeUrl", (Object) null);

        assertEquals("https://example.com/callback", normalizedMarkdown);
        assertEquals("https://example.com/callback", normalizedPlain);
        assertEquals("", normalizedNull);
    }
}
