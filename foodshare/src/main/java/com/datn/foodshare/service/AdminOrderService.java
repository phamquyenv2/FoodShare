package com.datn.foodshare.service;

import com.datn.foodshare.domain.response.OrderResponse;
import com.datn.foodshare.repository.OrderRepository;
import com.datn.foodshare.util.constant.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminOrderService {
    private final OrderRepository orderRepository;
    private final com.datn.foodshare.repository.PaymentRepository paymentRepository;
    private final com.datn.foodshare.service.payment.strategy.PaymentStrategyFactory paymentStrategyFactory;
    private final SupplierEarningService supplierEarningService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Page<OrderResponse> search(String keyword, OrderStatus status, Pageable pageable) {
        String normalized = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return orderRepository.adminSearch(normalized, status, pageable).map(OrderResponse::from);
    }

    @Transactional(readOnly = true)
    public OrderResponse detail(Long id) {
        return OrderResponse.from(orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Không tìm thấy đơn hàng")));
    }

    @Transactional
    public OrderResponse refundOrder(Long id) {
        com.datn.foodshare.domain.entity.Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new com.datn.foodshare.util.error.BusinessException("Không tìm thấy đơn hàng: " + id));
        java.util.List<com.datn.foodshare.domain.entity.Payment> payments = paymentRepository.findByOrderId(id);
        boolean refundedAny = false;
        for (com.datn.foodshare.domain.entity.Payment payment : payments) {
            if (payment.getPaymentStatus() == com.datn.foodshare.util.constant.TransactionStatus.SUCCESS) {
                com.datn.foodshare.service.payment.strategy.PaymentStrategy strategy = paymentStrategyFactory.getStrategy(payment.getMethod());
                com.datn.foodshare.domain.entity.Payment refundedPayment = strategy.processRefund(payment);
                paymentRepository.save(refundedPayment);
                supplierEarningService.reverseForRefundedPayment(refundedPayment);
                refundedAny = true;
            }
        }
        if (!refundedAny) {
            throw new com.datn.foodshare.util.error.BusinessException("Không có khoản thanh toán thành công nào để hoàn tiền cho đơn này");
        }

        eventPublisher.publishEvent(com.datn.foodshare.event.NotificationEvent.builder()
                .source(this)
                .user(order.getReceiver())
                .title("Khoản thanh toán đã được hoàn lại")
                .content("Admin đã hoàn tiền cho đơn hàng " + order.getOrderCode() + ".")
                .type(com.datn.foodshare.util.constant.NotificationType.PAYMENT)
                .referenceType(com.datn.foodshare.util.constant.NotificationReferenceType.ORDER)
                .referenceId(order.getId())
                .channels(java.util.Set.of(com.datn.foodshare.util.constant.NotificationChannel.IN_APP,
                        com.datn.foodshare.util.constant.NotificationChannel.PUSH,
                        com.datn.foodshare.util.constant.NotificationChannel.EMAIL))
                .build());

        com.datn.foodshare.domain.entity.Order reloaded = orderRepository.findByIdWithDetails(id).orElse(order);
        return OrderResponse.from(reloaded);
    }
}
