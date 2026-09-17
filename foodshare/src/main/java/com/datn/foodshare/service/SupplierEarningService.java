package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.Payment;
import com.datn.foodshare.domain.entity.SupplierEarning;
import com.datn.foodshare.repository.PaymentRepository;
import com.datn.foodshare.repository.SupplierEarningRepository;
import com.datn.foodshare.repository.SystemConfigRepository;
import com.datn.foodshare.util.constant.OrderStatus;
import com.datn.foodshare.util.constant.PaymentMethod;
import com.datn.foodshare.util.constant.TransactionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SupplierEarningService {

    private static final BigDecimal DEFAULT_FEE_RATE = new BigDecimal("0.05");

    private final SupplierEarningRepository supplierEarningRepository;
    private final PaymentRepository paymentRepository;
    private final SystemConfigRepository systemConfigRepository;

    @Transactional
    public Optional<SupplierEarning> recordForCompletedOrder(Order order) {
        if (order.getOrderStatus() != OrderStatus.COMPLETED
                || order.getTotalAmount() == null
                || order.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        Optional<SupplierEarning> existing = supplierEarningRepository.findByOrderId(order.getId());
        if (existing.isPresent()) {
            return existing;
        }

        Optional<Payment> eligiblePayment = paymentRepository.findByOrderId(order.getId()).stream()
                .filter(payment -> payment.getMethod() != PaymentMethod.CASH
                        && payment.getPaymentStatus() == TransactionStatus.SUCCESS)
                .findFirst();
        if (eligiblePayment.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal grossAmount = eligiblePayment.get().getAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal feeRate = getPlatformFeeRate();
        BigDecimal platformFee = grossAmount.multiply(feeRate).setScale(2, RoundingMode.HALF_UP);
        SupplierEarning earning = SupplierEarning.builder()
                .businessProfile(order.getBusinessProfile())
                .order(order)
                .payment(eligiblePayment.get())
                .grossAmount(grossAmount)
                .feeRate(feeRate)
                .platformFee(platformFee)
                .netAmount(grossAmount.subtract(platformFee))
                .earnedAt(order.getCompletedAt() == null ? Instant.now() : order.getCompletedAt())
                .build();
        return Optional.of(supplierEarningRepository.save(earning));
    }

    @Transactional
    public void reverseForRefundedPayment(Payment payment) {
        supplierEarningRepository.findByPaymentId(payment.getId()).ifPresent(earning -> {
            if (earning.isActive()) {
                earning.setReversedAt(payment.getRefundedAt() == null ? Instant.now() : payment.getRefundedAt());
                earning.setReversalReason("Payment refunded: " + payment.getId());
                supplierEarningRepository.save(earning);
            }
        });
    }

    private BigDecimal getPlatformFeeRate() {
        return systemConfigRepository.findByConfigKey("PLATFORM_FEE_PERCENTAGE")
                .map(config -> new BigDecimal(config.getConfigValue()))
                .orElse(DEFAULT_FEE_RATE);
    }
}
