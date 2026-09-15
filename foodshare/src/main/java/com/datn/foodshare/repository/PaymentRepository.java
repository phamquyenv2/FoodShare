package com.datn.foodshare.repository;

import com.datn.foodshare.domain.entity.Payment;
import com.datn.foodshare.util.constant.TransactionStatus;
import com.datn.foodshare.util.constant.PaymentMethod;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByOrderId(Long orderId);
    Optional<Payment> findByExternalTransactionId(String externalTransactionId);

    Optional<Payment> findFirstByOrderIdAndMethodAndPaymentStatusOrderByPaidAtDesc(
            Long orderId,
            PaymentMethod method,
            TransactionStatus paymentStatus);

    boolean existsByOrderIdAndPaymentStatus(Long orderId, TransactionStatus paymentStatus);

    @Query("SELECT p.order.id FROM Payment p WHERE p.id = :paymentId")
    Optional<Long> findOrderIdByPaymentId(@Param("paymentId") Long paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :paymentId")
    Optional<Payment> findByIdForUpdate(@Param("paymentId") Long paymentId);
}
