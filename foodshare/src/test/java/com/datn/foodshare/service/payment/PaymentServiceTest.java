package com.datn.foodshare.service.payment;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.Payment;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.request.CreatePaymentRequest;
import com.datn.foodshare.domain.response.PaymentResponse;
import com.datn.foodshare.event.NotificationEvent;
import com.datn.foodshare.repository.OrderRepository;
import com.datn.foodshare.repository.PaymentRepository;
import com.datn.foodshare.service.payment.strategy.CashPaymentStrategy;
import com.datn.foodshare.service.payment.strategy.MomoPaymentStrategy;
import com.datn.foodshare.service.payment.strategy.PaymentStrategyFactory;
import com.datn.foodshare.service.payment.strategy.ZaloPayPaymentStrategy;
import com.datn.foodshare.service.SupplierEarningService;
import com.datn.foodshare.util.SecurityUtil;
import com.datn.foodshare.util.constant.PaymentMethod;
import com.datn.foodshare.util.constant.NotificationChannel;
import com.datn.foodshare.util.constant.TransactionStatus;
import com.datn.foodshare.util.error.BusinessException;
import com.datn.foodshare.util.error.PermissionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    private PaymentStrategyFactory paymentStrategyFactory;
    @Mock
    private SupplierEarningService supplierEarningService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    private PaymentService paymentService;

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 100L;
    private static final Long PAYMENT_ID = 500L;

    @BeforeEach
    void setUp() {
        CashPaymentStrategy cashStrategy = new CashPaymentStrategy();
        MomoPaymentStrategy momoStrategy = mock(MomoPaymentStrategy.class);
        ZaloPayPaymentStrategy zaloPayStrategy = mock(ZaloPayPaymentStrategy.class);
        lenient().when(momoStrategy.createPayment(any(Order.class), any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(1);
            payment.setPaymentStatus(TransactionStatus.PROCESSING);
            payment.setProvider("MOMO");
            payment.setExternalTransactionId("MOMO-TEST");
            return payment;
        });
        lenient().when(momoStrategy.processRefund(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setPaymentStatus(TransactionStatus.REFUNDED);
            payment.setRefundedAt(java.time.Instant.now());
            return payment;
        });
        lenient().when(zaloPayStrategy.createPayment(any(Order.class), any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(1);
            payment.setPaymentStatus(TransactionStatus.PROCESSING);
            payment.setProvider("ZALOPAY");
            payment.setExternalTransactionId("ZALOPAY-TEST");
            return payment;
        });
        paymentStrategyFactory = new PaymentStrategyFactory(cashStrategy, momoStrategy, zaloPayStrategy);

        paymentService = new PaymentService(
                paymentRepository,
                orderRepository,
                paymentStrategyFactory,
                supplierEarningService,
                eventPublisher
        );
    }

    private User mockUser() {
        User user = new User();
        user.setId(USER_ID);
        return user;
    }

    private Order mockOrder(BigDecimal amount) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setReceiver(mockUser());
        order.setTotalAmount(amount);
        return order;
    }

    @Test
    void createPayment_success() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(USER_ID));
            
            Order order = mockOrder(new BigDecimal("50000"));
            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(java.util.List.of());
            
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(PAYMENT_ID);
                return p;
            });

            CreatePaymentRequest req = new CreatePaymentRequest(PaymentMethod.MOMO);
            PaymentResponse res = paymentService.createPayment(ORDER_ID, req);

            assertNotNull(res);
            assertEquals(TransactionStatus.PROCESSING, res.getStatus());
            assertEquals("MOMO", res.getProvider());
            assertNotNull(res.getExternalTransactionId());
        }
    }

    @Test
    void createPayment_failsIfFreeOrder() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(USER_ID));
            
            Order order = mockOrder(BigDecimal.ZERO);
            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
            
            CreatePaymentRequest req = new CreatePaymentRequest(PaymentMethod.MOMO);
            
            BusinessException ex = assertThrows(BusinessException.class, () -> paymentService.createPayment(ORDER_ID, req));
            assertTrue(ex.getMessage().contains("miễn phí không yêu cầu"));
        }
    }

    @Test
    void createPayment_retryCancelsOldPayment() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(USER_ID));
            
            Order order = mockOrder(new BigDecimal("50000"));
            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
            
            Payment oldPayment = new Payment();
            oldPayment.setId(PAYMENT_ID);
            oldPayment.setOrder(order);
            oldPayment.setPaymentStatus(TransactionStatus.PENDING);
            oldPayment.setMethod(PaymentMethod.CASH);
            
            when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(java.util.List.of(oldPayment));
            
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                if (p.getId() == null) p.setId(PAYMENT_ID + 1);
                return p;
            });

            CreatePaymentRequest req = new CreatePaymentRequest(PaymentMethod.MOMO);
            PaymentResponse res = paymentService.createPayment(ORDER_ID, req);

            assertEquals(TransactionStatus.CANCELLED, oldPayment.getPaymentStatus());
            verify(paymentRepository, times(2)).save(any(Payment.class));
            assertEquals(TransactionStatus.PROCESSING, res.getStatus());
        }
    }

    @Test
    void handlePaymentSuccess_success() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(USER_ID));

            Payment payment = new Payment();
            payment.setId(PAYMENT_ID);
            payment.setOrder(mockOrder(new BigDecimal("50000")));
            payment.setPaymentStatus(TransactionStatus.PROCESSING);

            stubLockedPayment(payment);
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            PaymentResponse res = paymentService.handlePaymentSuccess(PAYMENT_ID);

            assertEquals(TransactionStatus.SUCCESS, res.getStatus());
            assertNotNull(res.getPaidAt());
            NotificationEvent event = captureNotificationEvent();
            assertTrue(event.supports(NotificationChannel.PUSH));
            assertTrue(event.supports(NotificationChannel.EMAIL));
        }
    }

    @Test
    void handlePaymentFailure_success() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(USER_ID));

            Payment payment = new Payment();
            payment.setId(PAYMENT_ID);
            payment.setOrder(mockOrder(new BigDecimal("50000")));
            payment.setPaymentStatus(TransactionStatus.PROCESSING);

            stubLockedPayment(payment);
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            PaymentResponse res = paymentService.handlePaymentFailure(PAYMENT_ID);

            assertEquals(TransactionStatus.FAILED, res.getStatus());
            NotificationEvent event = captureNotificationEvent();
            assertTrue(event.supports(NotificationChannel.PUSH));
            assertFalse(event.supports(NotificationChannel.EMAIL));
        }
    }

    @Test
    void refundPayment_success() throws PermissionException {
        Payment payment = new Payment();
        payment.setId(PAYMENT_ID);
        payment.setOrder(mockOrder(new BigDecimal("50000")));
        payment.setMethod(PaymentMethod.MOMO);
        payment.setPaymentStatus(TransactionStatus.SUCCESS);

        stubLockedPayment(payment);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse res;
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(() -> SecurityUtil.hasRole("ADMIN")).thenReturn(true);
            res = paymentService.refundPayment(PAYMENT_ID);
        }

        assertEquals(TransactionStatus.REFUNDED, res.getStatus());
        NotificationEvent event = captureNotificationEvent();
        assertTrue(event.supports(NotificationChannel.PUSH));
        assertTrue(event.supports(NotificationChannel.EMAIL));
    }

    @Test
    void createPayment_unauthenticated_rejectsWithoutLoadingOrder() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.empty());

            assertThrows(PermissionException.class, () -> paymentService.createPayment(
                    ORDER_ID, new CreatePaymentRequest(PaymentMethod.CASH)));

            verifyNoInteractions(orderRepository, paymentRepository);
        }
    }

    @ParameterizedTest
    @EnumSource(value = com.datn.foodshare.util.constant.OrderStatus.class, names = {"CANCELLED", "REJECTED"})
    void createPayment_rejectsTerminalOrderStates(com.datn.foodshare.util.constant.OrderStatus orderStatus) {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(USER_ID));
            Order order = mockOrder(new BigDecimal("50000"));
            order.setOrderStatus(orderStatus);
            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

            assertThrows(BusinessException.class, () -> paymentService.createPayment(
                    ORDER_ID, new CreatePaymentRequest(PaymentMethod.MOMO)));

            verifyNoInteractions(paymentRepository);
        }
    }

    @Test
    void createPayment_orderOwnedByAnotherUser_rejectsWithoutSaving() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(USER_ID));
            Order order = mockOrder(new BigDecimal("50000"));
            order.getReceiver().setId(99L);
            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

            assertThrows(PermissionException.class, () -> paymentService.createPayment(
                    ORDER_ID, new CreatePaymentRequest(PaymentMethod.CASH)));

            verify(paymentRepository, never()).save(any());
        }
    }

    @Test
    void createPayment_existingSuccessfulPayment_rejectsDuplicate() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(USER_ID));
            Order order = mockOrder(new BigDecimal("50000"));
            Payment existing = payment(order, TransactionStatus.SUCCESS);
            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(java.util.List.of(existing));

            assertThrows(BusinessException.class, () -> paymentService.createPayment(
                    ORDER_ID, new CreatePaymentRequest(PaymentMethod.MOMO)));

            verify(paymentRepository, never()).save(any());
        }
    }

    @Test
    void createPayment_cash_usesPendingCashStrategy() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(USER_ID));
            Order order = mockOrder(new BigDecimal("50000"));
            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(java.util.List.of());
            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

            PaymentResponse result = paymentService.createPayment(
                    ORDER_ID, new CreatePaymentRequest(PaymentMethod.CASH));

            assertEquals(TransactionStatus.PENDING, result.getStatus());
            assertEquals("CASH", result.getProvider());
        }
    }

    @Test
    void handlePaymentSuccess_alreadySuccessful_isIdempotent() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(USER_ID));
            Payment payment = payment(mockOrder(new BigDecimal("50000")), TransactionStatus.SUCCESS);
            stubLockedPayment(payment);

            PaymentResponse result = paymentService.handlePaymentSuccess(PAYMENT_ID);

            assertEquals(TransactionStatus.SUCCESS, result.getStatus());
            verify(paymentRepository, never()).save(any());
        }
    }

    @ParameterizedTest
    @EnumSource(value = TransactionStatus.class, names = {"FAILED", "CANCELLED", "EXPIRED", "REFUNDED"})
    void handlePaymentSuccess_terminalPaymentCannotBeRevived(TransactionStatus status) {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(USER_ID));
            stubLockedPayment(payment(mockOrder(new BigDecimal("50000")), status));

            assertThrows(BusinessException.class, () -> paymentService.handlePaymentSuccess(PAYMENT_ID));

            verify(paymentRepository, never()).save(any());
        }
    }

    @Test
    void handlePaymentSuccess_paymentOwnedByAnotherUser_rejects() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(USER_ID));
            Order order = mockOrder(new BigDecimal("50000"));
            order.getReceiver().setId(99L);
            stubLockedPayment(payment(order, TransactionStatus.PROCESSING));

            assertThrows(PermissionException.class,
                    () -> paymentService.handlePaymentSuccess(PAYMENT_ID));

            verify(paymentRepository, never()).save(any());
        }
    }

    @Test
    void handlePaymentFailure_successfulPayment_cannotBeChanged() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(USER_ID));
            stubLockedPayment(payment(mockOrder(new BigDecimal("50000")), TransactionStatus.SUCCESS));

            assertThrows(BusinessException.class,
                    () -> paymentService.handlePaymentFailure(PAYMENT_ID));

            verify(paymentRepository, never()).save(any());
        }
    }

    @Test
    void handlePaymentFailure_alreadyFailed_isIdempotent() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(USER_ID));
            stubLockedPayment(payment(mockOrder(new BigDecimal("50000")), TransactionStatus.FAILED));

            PaymentResponse result = paymentService.handlePaymentFailure(PAYMENT_ID);

            assertEquals(TransactionStatus.FAILED, result.getStatus());
            verify(paymentRepository, never()).save(any());
        }
    }

    @ParameterizedTest
    @EnumSource(value = TransactionStatus.class, names = {"SUCCESS", "CANCELLED", "EXPIRED", "REFUNDED"})
    void handlePaymentFailure_terminalPaymentCannotChange(TransactionStatus status) {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(USER_ID));
            stubLockedPayment(payment(mockOrder(new BigDecimal("50000")), status));

            assertThrows(BusinessException.class, () -> paymentService.handlePaymentFailure(PAYMENT_ID));

            verify(paymentRepository, never()).save(any());
        }
    }

    @Test
    void refundPayment_nonSuccessfulPayment_rejects() {
        Payment payment = payment(mockOrder(new BigDecimal("50000")), TransactionStatus.FAILED);
        stubLockedPayment(payment);

        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(() -> SecurityUtil.hasRole("ADMIN")).thenReturn(true);
            assertThrows(BusinessException.class, () -> paymentService.refundPayment(PAYMENT_ID));
        }

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void refundPayment_alreadyRefunded_isIdempotent() throws PermissionException {
        Payment payment = payment(mockOrder(new BigDecimal("50000")), TransactionStatus.REFUNDED);
        stubLockedPayment(payment);

        PaymentResponse result;
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(() -> SecurityUtil.hasRole("ADMIN")).thenReturn(true);
            result = paymentService.refundPayment(PAYMENT_ID);
        }

        assertEquals(TransactionStatus.REFUNDED, result.getStatus());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void refundPayment_nonAdmin_rejectsBeforeLoadingPayment() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(() -> SecurityUtil.hasRole("ADMIN")).thenReturn(false);

            assertThrows(PermissionException.class, () -> paymentService.refundPayment(PAYMENT_ID));
        }

        verifyNoInteractions(orderRepository, paymentRepository, supplierEarningService);
    }

    @Test
    void processMomoCallback_success_marksPaymentAndPublishesNotification() {
        Payment payment = payment(mockOrder(new BigDecimal("50000")), TransactionStatus.PROCESSING);
        payment.setMethod(PaymentMethod.MOMO);
        payment.setExternalTransactionId("momo-order-1");
        when(paymentRepository.findByExternalTransactionId("momo-order-1")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        paymentService.processMomoCallback(Map.of(
                "resultCode", 0,
                "orderId", "momo-order-1",
                "amount", 50000));

        assertEquals(TransactionStatus.SUCCESS, payment.getPaymentStatus());
        assertNotNull(payment.getPaidAt());
        verify(paymentRepository).save(payment);
        assertTrue(captureNotificationEvent().supports(NotificationChannel.EMAIL));
    }

    @Test
    void processMomoCallback_amountMismatch_doesNotChangePayment() {
        Payment payment = payment(mockOrder(new BigDecimal("50000")), TransactionStatus.PROCESSING);
        payment.setExternalTransactionId("momo-order-2");
        when(paymentRepository.findByExternalTransactionId("momo-order-2")).thenReturn(Optional.of(payment));

        paymentService.processMomoCallback(Map.of(
                "resultCode", "0",
                "orderId", "momo-order-2",
                "amount", 49999));

        assertEquals(TransactionStatus.PROCESSING, payment.getPaymentStatus());
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void processMomoRedirect_success_marksOwnedPayment() throws PermissionException {
        Payment payment = payment(mockOrder(new BigDecimal("50000")), TransactionStatus.PROCESSING);
        payment.setExternalTransactionId("momo-order-3");
        when(paymentRepository.findByExternalTransactionId("momo-order-3")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response;
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(USER_ID));
            response = paymentService.processMomoRedirect("momo-order-3", "0", 50000L);
        }

        assertEquals(TransactionStatus.SUCCESS, response.getStatus());
        verify(paymentRepository).save(payment);
    }

    @Test
    void processMomoRedirect_nonSuccessfulResult_rejectsBeforeLoadingPayment() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            assertThrows(BusinessException.class,
                    () -> paymentService.processMomoRedirect("momo-order-4", "1", 50000L));
        }

        verifyNoInteractions(paymentRepository, orderRepository);
    }

    @Test
    void processZaloPayCallback_nestedPayload_marksPaymentSuccessful() {
        Payment payment = payment(mockOrder(new BigDecimal("75000")), TransactionStatus.PROCESSING);
        payment.setExternalTransactionId("zalo-order-1");
        when(paymentRepository.findByExternalTransactionId("zalo-order-1")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        paymentService.processZaloPayCallback(Map.of(
                "return_code", 1,
                "data", "{\"app_trans_id\":\"zalo-order-1\",\"amount\":75000}"));

        assertEquals(TransactionStatus.SUCCESS, payment.getPaymentStatus());
        verify(paymentRepository).save(payment);
    }

    @Test
    void processZaloPayCallback_tokenWithoutAmount_isIgnored() {
        Payment payment = payment(mockOrder(new BigDecimal("75000")), TransactionStatus.PROCESSING);
        payment.setExternalTransactionId("zalo-order-2");
        when(paymentRepository.findByExternalTransactionId("zalo-order-2")).thenReturn(Optional.of(payment));

        paymentService.processZaloPayCallback(Map.of(
                "returncode", 1,
                "zptranstoken", "zalo-order-2"));

        assertEquals(TransactionStatus.PROCESSING, payment.getPaymentStatus());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processZaloPayRedirect_success_marksOwnedPayment() throws PermissionException {
        Payment payment = payment(mockOrder(new BigDecimal("85000")), TransactionStatus.PROCESSING);
        payment.setExternalTransactionId("zalo-order-3");
        when(paymentRepository.findByExternalTransactionId("zalo-order-3")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response;
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(USER_ID));
            response = paymentService.processZaloPayRedirect("zalo-order-3", "1", 85000L);
        }

        assertEquals(TransactionStatus.SUCCESS, response.getStatus());
        verify(paymentRepository).save(payment);
    }

    private Payment payment(Order order, TransactionStatus status) {
        Payment payment = new Payment();
        payment.setId(PAYMENT_ID);
        payment.setOrder(order);
        payment.setAmount(order.getTotalAmount());
        payment.setMethod(PaymentMethod.CASH);
        payment.setPaymentStatus(status);
        return payment;
    }

    private void stubLockedPayment(Payment payment) {
        when(paymentRepository.findOrderIdByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(ORDER_ID));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(payment.getOrder()));
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));
    }

    private NotificationEvent captureNotificationEvent() {
        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }
}
