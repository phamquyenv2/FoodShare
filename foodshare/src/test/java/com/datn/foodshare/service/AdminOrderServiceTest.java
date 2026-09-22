package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.Payment;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.response.OrderResponse;
import com.datn.foodshare.event.NotificationEvent;
import com.datn.foodshare.repository.OrderRepository;
import com.datn.foodshare.repository.PaymentRepository;
import com.datn.foodshare.service.payment.strategy.PaymentStrategy;
import com.datn.foodshare.service.payment.strategy.PaymentStrategyFactory;
import com.datn.foodshare.util.constant.OrderStatus;
import com.datn.foodshare.util.constant.PaymentMethod;
import com.datn.foodshare.util.constant.Role;
import com.datn.foodshare.util.constant.TransactionStatus;
import com.datn.foodshare.util.error.BusinessException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentStrategyFactory paymentStrategyFactory;
    @Mock
    private SupplierEarningService supplierEarningService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AdminOrderService adminOrderService;

    @BeforeEach
    void setUp() {
        adminOrderService = new AdminOrderService(
                orderRepository,
                paymentRepository,
                paymentStrategyFactory,
                supplierEarningService,
                eventPublisher
        );
    }

    private Order createSampleOrder(Long id, OrderStatus status) {
        User receiver = User.builder()
                .id(2L)
                .email("receiver@example.com")
                .fullName("Receiver User")
                .role(Role.RECIPIENT)
                .build();

        return Order.builder()
                .id(id)
                .orderCode("FS-ORD-" + id)
                .receiver(receiver)
                .orderStatus(status)
                .totalAmount(BigDecimal.valueOf(50000))
                .createdAt(Instant.now())
                .orderDetails(List.of())
                .payments(List.of())
                .build();
    }

    @Test
    void search_withBlankKeyword_normalizesToNull() {
        Pageable pageable = PageRequest.of(0, 10);
        Order order = createSampleOrder(1L, OrderStatus.COMPLETED);
        when(orderRepository.adminSearch(null, OrderStatus.COMPLETED, pageable))
                .thenReturn(new PageImpl<>(List.of(order)));

        Page<OrderResponse> result = adminOrderService.search("   ", OrderStatus.COMPLETED, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1L, result.getContent().get(0).getId());
        verify(orderRepository).adminSearch(null, OrderStatus.COMPLETED, pageable);
    }

    @Test
    void search_withValidKeyword_trimsKeyword() {
        Pageable pageable = PageRequest.of(0, 10);
        Order order = createSampleOrder(1L, OrderStatus.COMPLETED);
        when(orderRepository.adminSearch("ORD-123", OrderStatus.COMPLETED, pageable))
                .thenReturn(new PageImpl<>(List.of(order)));

        Page<OrderResponse> result = adminOrderService.search("  ORD-123  ", OrderStatus.COMPLETED, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(orderRepository).adminSearch("ORD-123", OrderStatus.COMPLETED, pageable);
    }

    @Test
    void detail_existingOrder_returnsResponse() {
        Order order = createSampleOrder(10L, OrderStatus.ACCEPTED);
        when(orderRepository.findByIdWithDetails(10L)).thenReturn(Optional.of(order));

        OrderResponse response = adminOrderService.detail(10L);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals("FS-ORD-10", response.getOrderCode());
    }

    @Test
    void detail_nonExistingOrder_throwsEntityNotFoundException() {
        when(orderRepository.findByIdWithDetails(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> adminOrderService.detail(99L));
    }

    @Test
    void refundOrder_orderNotFound_throwsBusinessException() {
        when(orderRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> adminOrderService.refundOrder(99L));
        assertTrue(ex.getMessage().contains("Không tìm thấy đơn hàng"));
    }

    @Test
    void refundOrder_noSuccessfulPayments_throwsBusinessException() {
        Order order = createSampleOrder(5L, OrderStatus.CANCELLED);
        when(orderRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        Payment pendingPayment = Payment.builder()
                .id(100L)
                .order(order)
                .method(PaymentMethod.MOMO)
                .paymentStatus(TransactionStatus.PENDING)
                .amount(BigDecimal.valueOf(50000))
                .build();
        when(paymentRepository.findByOrderId(5L)).thenReturn(List.of(pendingPayment));

        BusinessException ex = assertThrows(BusinessException.class, () -> adminOrderService.refundOrder(5L));
        assertTrue(ex.getMessage().contains("Không có khoản thanh toán thành công"));
        verifyNoInteractions(paymentStrategyFactory);
        verifyNoInteractions(supplierEarningService);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void refundOrder_successPayment_processesRefundAndPublishesNotification() {
        Order order = createSampleOrder(7L, OrderStatus.ACCEPTED);
        when(orderRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(order));

        Payment successPayment = Payment.builder()
                .id(200L)
                .order(order)
                .method(PaymentMethod.MOMO)
                .paymentStatus(TransactionStatus.SUCCESS)
                .amount(BigDecimal.valueOf(100000))
                .build();
        Payment failedPayment = Payment.builder()
                .id(201L)
                .order(order)
                .method(PaymentMethod.ZALOPAY)
                .paymentStatus(TransactionStatus.FAILED)
                .amount(BigDecimal.valueOf(100000))
                .build();

        when(paymentRepository.findByOrderId(7L)).thenReturn(List.of(failedPayment, successPayment));

        PaymentStrategy momoStrategy = mock(PaymentStrategy.class);
        when(paymentStrategyFactory.getStrategy(PaymentMethod.MOMO)).thenReturn(momoStrategy);

        Payment refundedPayment = Payment.builder()
                .id(200L)
                .order(order)
                .method(PaymentMethod.MOMO)
                .paymentStatus(TransactionStatus.REFUNDED)
                .amount(BigDecimal.valueOf(100000))
                .refundedAt(Instant.now())
                .build();
        when(momoStrategy.processRefund(successPayment)).thenReturn(refundedPayment);
        when(orderRepository.findByIdWithDetails(7L)).thenReturn(Optional.of(order));

        OrderResponse response = adminOrderService.refundOrder(7L);

        assertNotNull(response);
        assertEquals(7L, response.getId());

        verify(paymentRepository).save(refundedPayment);
        verify(supplierEarningService).reverseForRefundedPayment(refundedPayment);

        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        NotificationEvent event = eventCaptor.getValue();
        assertEquals("Khoản thanh toán đã được hoàn lại", event.getTitle());
        assertEquals(order.getReceiver().getId(), event.getUser().getId());
        assertEquals(7L, event.getReferenceId());
    }
}
