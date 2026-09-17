package com.datn.foodshare.service;

import com.datn.foodshare.domain.response.PaymentResponse;
import com.datn.foodshare.repository.PaymentRepository;
import com.datn.foodshare.util.constant.TransactionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminPaymentService {
    private final PaymentRepository paymentRepository;
    private final com.datn.foodshare.service.payment.strategy.PaymentStrategyFactory paymentStrategyFactory;
    private final SupplierEarningService supplierEarningService;

    @Transactional(readOnly = true)
    public Page<PaymentResponse> search(String keyword, TransactionStatus status, Pageable pageable) {
        String normalized = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return paymentRepository.adminSearch(normalized, status, pageable).map(PaymentResponse::from);
    }

    @Transactional(readOnly = true)
    public PaymentResponse detail(Long id) {
        return PaymentResponse.from(paymentRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Không tìm thấy giao dịch")));
    }

    @Transactional
    public PaymentResponse refund(Long id) {
        com.datn.foodshare.domain.entity.Payment payment = paymentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new com.datn.foodshare.util.error.BusinessException("Không tìm thấy giao dịch thanh toán: " + id));
        if (payment.getPaymentStatus() == TransactionStatus.REFUNDED) {
            throw new com.datn.foodshare.util.error.BusinessException("Giao dịch này đã được hoàn tiền trước đó");
        }
        if (payment.getPaymentStatus() != TransactionStatus.SUCCESS) {
            throw new com.datn.foodshare.util.error.BusinessException("Chỉ có thể hoàn tiền cho giao dịch thành công");
        }
        com.datn.foodshare.service.payment.strategy.PaymentStrategy strategy = paymentStrategyFactory.getStrategy(payment.getMethod());
        com.datn.foodshare.domain.entity.Payment refundedPayment = strategy.processRefund(payment);
        com.datn.foodshare.domain.entity.Payment saved = paymentRepository.save(refundedPayment);
        supplierEarningService.reverseForRefundedPayment(saved);
        return PaymentResponse.from(saved);
    }
}
