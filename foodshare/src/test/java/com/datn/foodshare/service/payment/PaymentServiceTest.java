package com.datn.foodshare.service.payment;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.Payment;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.request.CreatePaymentRequest;
import com.datn.foodshare.domain.response.PaymentResponse;
import com.datn.foodshare.repository.OrderRepository;
import com.datn.foodshare.repository.PaymentRepository;
import com.datn.foodshare.service.payment.strategy.CashPaymentStrategy;
import com.datn.foodshare.service.payment.strategy.EWalletPaymentStrategy;
import com.datn.foodshare.service.payment.strategy.PaymentStrategyFactory;
import com.datn.foodshare.util.SecurityUtil;
import com.datn.foodshare.util.constant.PaymentMethod;
import com.datn.foodshare.util.constant.TransactionStatus;
import com.datn.foodshare.util.error.BusinessException;
import com.datn.foodshare.util.error.PermissionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
    private PaymentService paymentService;

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 100L;
    private static final Long PAYMENT_ID = 500L;

    @BeforeEach
    void setUp() {
        CashPaymentStrategy cashStrategy = new CashPaymentStrategy();
        EWalletPaymentStrategy eWalletStrategy = new EWalletPaymentStrategy();
        paymentStrategyFactory = new PaymentStrategyFactory(cashStrategy, eWalletStrategy);

        paymentService = new PaymentService(
                paymentRepository,
                orderRepository,
                paymentStrategyFactory
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
            when(orderRepository.findByIdWithDetails(ORDER_ID)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(java.util.List.of());
            
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(PAYMENT_ID);
                return p;
            });

            CreatePaymentRequest req = new CreatePaymentRequest(PaymentMethod.EWALLET);
            PaymentResponse res = paymentService.createPayment(ORDER_ID, req);

            assertNotNull(res);
            assertEquals(TransactionStatus.PROCESSING, res.getStatus());
            assertEquals("EWALLET", res.getProvider());
            assertNotNull(res.getExternalTransactionId());
        }
    }

    @Test
    void createPayment_failsIfFreeOrder() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(USER_ID));
            
            Order order = mockOrder(BigDecimal.ZERO);
            when(orderRepository.findByIdWithDetails(ORDER_ID)).thenReturn(Optional.of(order));
            
            CreatePaymentRequest req = new CreatePaymentRequest(PaymentMethod.EWALLET);
            
            BusinessException ex = assertThrows(BusinessException.class, () -> paymentService.createPayment(ORDER_ID, req));
            assertTrue(ex.getMessage().contains("miễn phí không yêu cầu"));
        }
    }

    @Test
    void createPayment_retryCancelsOldPayment() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(USER_ID));
            
            Order order = mockOrder(new BigDecimal("50000"));
            when(orderRepository.findByIdWithDetails(ORDER_ID)).thenReturn(Optional.of(order));
            
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

            CreatePaymentRequest req = new CreatePaymentRequest(PaymentMethod.EWALLET);
            PaymentResponse res = paymentService.createPayment(ORDER_ID, req);

            assertEquals(TransactionStatus.CANCELLED, oldPayment.getPaymentStatus());
            verify(paymentRepository, times(2)).save(any(Payment.class));
            assertEquals(TransactionStatus.PROCESSING, res.getStatus());
        }
    }

    @Test
    void handlePaymentSuccess_success() {
        Payment payment = new Payment();
        payment.setId(PAYMENT_ID);
        payment.setOrder(mockOrder(new BigDecimal("50000")));
        payment.setPaymentStatus(TransactionStatus.PROCESSING);

        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse res = paymentService.handlePaymentSuccess(PAYMENT_ID);

        assertEquals(TransactionStatus.SUCCESS, res.getStatus());
        assertNotNull(res.getPaidAt());
    }

    @Test
    void handlePaymentFailure_success() {
        Payment payment = new Payment();
        payment.setId(PAYMENT_ID);
        payment.setOrder(mockOrder(new BigDecimal("50000")));
        payment.setPaymentStatus(TransactionStatus.PROCESSING);

        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse res = paymentService.handlePaymentFailure(PAYMENT_ID);

        assertEquals(TransactionStatus.FAILED, res.getStatus());
    }

    @Test
    void refundPayment_success() {
        Payment payment = new Payment();
        payment.setId(PAYMENT_ID);
        payment.setOrder(mockOrder(new BigDecimal("50000")));
        payment.setMethod(PaymentMethod.EWALLET);
        payment.setPaymentStatus(TransactionStatus.SUCCESS);

        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse res = paymentService.refundPayment(PAYMENT_ID);

        assertEquals(TransactionStatus.REFUNDED, res.getStatus());
    }
}
