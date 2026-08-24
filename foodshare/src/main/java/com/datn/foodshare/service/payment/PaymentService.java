package com.datn.foodshare.service.payment;

import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.Payment;
import com.datn.foodshare.domain.request.CreatePaymentRequest;
import com.datn.foodshare.domain.response.PaymentResponse;
import com.datn.foodshare.repository.OrderRepository;
import com.datn.foodshare.repository.PaymentRepository;
import com.datn.foodshare.service.payment.strategy.PaymentStrategy;
import com.datn.foodshare.service.payment.strategy.PaymentStrategyFactory;
import com.datn.foodshare.util.SecurityUtil;
import com.datn.foodshare.util.constant.TransactionStatus;
import com.datn.foodshare.util.error.BusinessException;
import com.datn.foodshare.util.error.PermissionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentStrategyFactory paymentStrategyFactory;

    @Transactional
    public PaymentResponse createPayment(Long orderId, CreatePaymentRequest request) throws PermissionException {
        Long currentUserId = SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new PermissionException("Chưa đăng nhập"));

        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new BusinessException("Đơn hàng không tồn tại: " + orderId));

        if (!order.getReceiver().getId().equals(currentUserId)) {
            throw new PermissionException("Bạn không có quyền thanh toán đơn hàng này");
        }

        if (order.getTotalAmount() == null || order.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Đơn hàng miễn phí không yêu cầu thanh toán");
        }

        List<Payment> existingPayments = paymentRepository.findByOrderId(orderId);
        for (Payment existingPayment : existingPayments) {
            if (existingPayment.getPaymentStatus() == TransactionStatus.SUCCESS) {
                throw new BusinessException("Đơn hàng đã được thanh toán");
            }
            if (existingPayment.getPaymentStatus() == TransactionStatus.PENDING || 
                existingPayment.getPaymentStatus() == TransactionStatus.PROCESSING) {
                existingPayment.setPaymentStatus(TransactionStatus.CANCELLED);
                paymentRepository.save(existingPayment);
            }
        }

        Payment payment = Payment.builder()
                .order(order)
                .amount(order.getTotalAmount())
                .method(request.getMethod())
                .paymentStatus(TransactionStatus.PENDING)
                .build();

        PaymentStrategy strategy = paymentStrategyFactory.getStrategy(request.getMethod());
        payment = strategy.processPayment(order, payment);

        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentResponse handlePaymentSuccess(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException("Giao dịch không tồn tại"));

        if (payment.getPaymentStatus() == TransactionStatus.SUCCESS) {
            return PaymentResponse.from(payment);
        }

        payment.setPaymentStatus(TransactionStatus.SUCCESS);
        payment.setPaidAt(Instant.now());
        
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentResponse handlePaymentFailure(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException("Giao dịch không tồn tại"));

        if (payment.getPaymentStatus() == TransactionStatus.SUCCESS) {
            throw new BusinessException("Giao dịch đã thành công, không thể chuyển sang thất bại");
        }

        payment.setPaymentStatus(TransactionStatus.FAILED);
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentResponse refundPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException("Giao dịch không tồn tại"));

        if (payment.getPaymentStatus() != TransactionStatus.SUCCESS) {
            throw new BusinessException("Chỉ có thể hoàn tiền các giao dịch đã thành công");
        }

        PaymentStrategy strategy = paymentStrategyFactory.getStrategy(payment.getMethod());
        payment = strategy.processRefund(payment);

        return PaymentResponse.from(paymentRepository.save(payment));
    }
}
