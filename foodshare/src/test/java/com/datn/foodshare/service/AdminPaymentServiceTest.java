package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.Payment;
import com.datn.foodshare.domain.response.PaymentResponse;
import com.datn.foodshare.repository.PaymentRepository;
import com.datn.foodshare.service.payment.strategy.PaymentStrategy;
import com.datn.foodshare.service.payment.strategy.PaymentStrategyFactory;
import com.datn.foodshare.util.constant.PaymentMethod;
import com.datn.foodshare.util.constant.TransactionStatus;
import com.datn.foodshare.util.error.BusinessException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminPaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentStrategyFactory paymentStrategyFactory;
    @Mock
    private SupplierEarningService supplierEarningService;

    private AdminPaymentService adminPaymentService;

    @BeforeEach
    void setUp() {
        adminPaymentService = new AdminPaymentService(
                paymentRepository,
                paymentStrategyFactory,
                supplierEarningService
        );
    }

    private Payment createSamplePayment(Long id, TransactionStatus status) {
        Order order = Order.builder()
                .id(100L)
                .orderCode("FS-ORD-100")
                .build();

        return Payment.builder()
                .id(id)
                .order(order)
                .method(PaymentMethod.MOMO)
                .provider("MOMO")
                .paymentStatus(status)
                .amount(BigDecimal.valueOf(150000))
                .externalTransactionId("EXT-" + id)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void search_withBlankKeyword_normalizesToNull() {
        Pageable pageable = PageRequest.of(0, 10);
        Payment payment = createSamplePayment(1L, TransactionStatus.SUCCESS);
        when(paymentRepository.adminSearch(null, TransactionStatus.SUCCESS, pageable))
                .thenReturn(new PageImpl<>(List.of(payment)));

        Page<PaymentResponse> result = adminPaymentService.search("   ", TransactionStatus.SUCCESS, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1L, result.getContent().get(0).getId());
        verify(paymentRepository).adminSearch(null, TransactionStatus.SUCCESS, pageable);
    }

    @Test
    void search_withKeyword_trimsKeyword() {
        Pageable pageable = PageRequest.of(0, 10);
        Payment payment = createSamplePayment(1L, TransactionStatus.SUCCESS);
        when(paymentRepository.adminSearch("EXT-123", TransactionStatus.SUCCESS, pageable))
                .thenReturn(new PageImpl<>(List.of(payment)));

        Page<PaymentResponse> result = adminPaymentService.search("  EXT-123  ", TransactionStatus.SUCCESS, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(paymentRepository).adminSearch("EXT-123", TransactionStatus.SUCCESS, pageable);
    }

    @Test
    void detail_existingPayment_returnsResponse() {
        Payment payment = createSamplePayment(5L, TransactionStatus.SUCCESS);
        when(paymentRepository.findById(5L)).thenReturn(Optional.of(payment));

        PaymentResponse response = adminPaymentService.detail(5L);

        assertNotNull(response);
        assertEquals(5L, response.getId());
        assertEquals("MOMO", response.getProvider());
    }

    @Test
    void detail_nonExistingPayment_throwsEntityNotFoundException() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> adminPaymentService.detail(999L));
    }

    @Test
    void refund_notFound_throwsBusinessException() {
        when(paymentRepository.findByIdForUpdate(88L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> adminPaymentService.refund(88L));
        assertTrue(ex.getMessage().contains("Không tìm thấy giao dịch"));
    }

    @Test
    void refund_alreadyRefunded_throwsBusinessException() {
        Payment payment = createSamplePayment(88L, TransactionStatus.REFUNDED);
        when(paymentRepository.findByIdForUpdate(88L)).thenReturn(Optional.of(payment));

        BusinessException ex = assertThrows(BusinessException.class, () -> adminPaymentService.refund(88L));
        assertTrue(ex.getMessage().contains("đã được hoàn tiền trước đó"));
    }

    @Test
    void refund_notSuccess_throwsBusinessException() {
        Payment payment = createSamplePayment(88L, TransactionStatus.PENDING);
        when(paymentRepository.findByIdForUpdate(88L)).thenReturn(Optional.of(payment));

        BusinessException ex = assertThrows(BusinessException.class, () -> adminPaymentService.refund(88L));
        assertTrue(ex.getMessage().contains("Chỉ có thể hoàn tiền cho giao dịch thành công"));
    }

    @Test
    void refund_successfulPayment_processesRefund() {
        Payment payment = createSamplePayment(10L, TransactionStatus.SUCCESS);
        when(paymentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(payment));

        PaymentStrategy strategy = mock(PaymentStrategy.class);
        when(paymentStrategyFactory.getStrategy(PaymentMethod.MOMO)).thenReturn(strategy);

        Payment refundedPayment = createSamplePayment(10L, TransactionStatus.REFUNDED);
        refundedPayment.setRefundedAt(Instant.now());
        when(strategy.processRefund(payment)).thenReturn(refundedPayment);
        when(paymentRepository.save(refundedPayment)).thenReturn(refundedPayment);

        PaymentResponse response = adminPaymentService.refund(10L);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals(TransactionStatus.REFUNDED, response.getStatus());

        verify(paymentRepository).save(refundedPayment);
        verify(supplierEarningService).reverseForRefundedPayment(refundedPayment);
    }
}
