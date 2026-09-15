package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.Payment;
import com.datn.foodshare.domain.entity.SupplierEarning;
import com.datn.foodshare.repository.PaymentRepository;
import com.datn.foodshare.repository.SupplierEarningRepository;
import com.datn.foodshare.repository.SystemConfigRepository;
import com.datn.foodshare.util.constant.OrderStatus;
import com.datn.foodshare.util.constant.PaymentMethod;
import com.datn.foodshare.util.constant.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupplierEarningServiceTest {

    @Mock
    private SupplierEarningRepository earningRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private SystemConfigRepository systemConfigRepository;

    private SupplierEarningService service;

    @BeforeEach
    void setUp() {
        service = new SupplierEarningService(
                earningRepository, paymentRepository, systemConfigRepository);
    }

    @Test
    void recordForCompletedOrder_snapshotsOnlineRevenueAndFee() {
        Order order = completedOrder();
        Payment payment = onlinePayment(order);
        when(earningRepository.findByOrderId(10L)).thenReturn(Optional.empty());
        when(paymentRepository.findByOrderId(10L)).thenReturn(List.of(payment));
        var config = new com.datn.foodshare.domain.entity.SystemConfig();
        config.setConfigValue("0.075");
        when(systemConfigRepository.findByConfigKey("PLATFORM_FEE_PERCENTAGE"))
                .thenReturn(Optional.of(config));
        when(earningRepository.save(any(SupplierEarning.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SupplierEarning result = service.recordForCompletedOrder(order).orElseThrow();

        assertEquals(new BigDecimal("100000.00"), result.getGrossAmount());
        assertEquals(new BigDecimal("0.075"), result.getFeeRate());
        assertEquals(new BigDecimal("7500.00"), result.getPlatformFee());
        assertEquals(new BigDecimal("92500.00"), result.getNetAmount());
    }

    @Test
    void recordForCompletedOrder_whenNoSuccessfulOnlinePayment_doesNotCreateEarning() {
        Order order = completedOrder();
        when(earningRepository.findByOrderId(10L)).thenReturn(Optional.empty());
        when(paymentRepository.findByOrderId(10L)).thenReturn(List.of());

        assertTrue(service.recordForCompletedOrder(order).isEmpty());
        verify(earningRepository, never()).save(any());
        verifyNoInteractions(systemConfigRepository);
    }

    @Test
    void reverseForRefundedPayment_isIdempotent() {
        Order order = completedOrder();
        Payment payment = onlinePayment(order);
        payment.setRefundedAt(Instant.parse("2026-09-06T08:00:00Z"));
        SupplierEarning earning = new SupplierEarning();
        when(earningRepository.findByPaymentId(20L)).thenReturn(Optional.of(earning));

        service.reverseForRefundedPayment(payment);
        service.reverseForRefundedPayment(payment);

        assertEquals(payment.getRefundedAt(), earning.getReversedAt());
        verify(earningRepository, times(1)).save(earning);
    }

    private Order completedOrder() {
        BusinessProfile profile = new BusinessProfile();
        profile.setId(5L);
        Order order = new Order();
        order.setId(10L);
        order.setBusinessProfile(profile);
        order.setOrderStatus(OrderStatus.COMPLETED);
        order.setTotalAmount(new BigDecimal("100000.00"));
        order.setCompletedAt(Instant.parse("2026-09-06T07:00:00Z"));
        return order;
    }

    private Payment onlinePayment(Order order) {
        Payment payment = new Payment();
        payment.setId(20L);
        payment.setOrder(order);
        payment.setAmount(order.getTotalAmount());
        payment.setMethod(PaymentMethod.MOMO);
        payment.setPaymentStatus(TransactionStatus.SUCCESS);
        return payment;
    }
}
