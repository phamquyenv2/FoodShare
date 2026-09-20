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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @Query("""
            SELECT p FROM Payment p JOIN FETCH p.order o
            WHERE (:keyword IS NULL OR LOWER(o.orderCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
              OR LOWER(p.externalTransactionId) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR p.paymentStatus = :status)
            """)
    Page<Payment> adminSearch(@Param("keyword") String keyword, @Param("status") TransactionStatus status, Pageable pageable);

    @Query(value = """
            SELECT p FROM Payment p JOIN FETCH p.order o LEFT JOIN FETCH o.businessProfile bp
            WHERE o.receiver.id = :userId
              AND (:keyword IS NULL OR LOWER(o.orderCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(p.externalTransactionId) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR p.paymentStatus = :status)
              AND (:method IS NULL OR p.method = :method)
            """,
            countQuery = """
            SELECT count(p) FROM Payment p
            WHERE p.order.receiver.id = :userId
              AND (:keyword IS NULL OR LOWER(p.order.orderCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(p.externalTransactionId) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR p.paymentStatus = :status)
              AND (:method IS NULL OR p.method = :method)
            """)
    Page<Payment> findByReceiverId(
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("status") TransactionStatus status,
            @Param("method") PaymentMethod method,
            Pageable pageable);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.order.receiver.id = :userId AND p.paymentStatus = com.datn.foodshare.util.constant.TransactionStatus.SUCCESS")
    BigDecimal sumSpentByReceiverId(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.order.receiver.id = :userId AND p.paymentStatus = com.datn.foodshare.util.constant.TransactionStatus.REFUNDED")
    BigDecimal sumRefundedByReceiverId(@Param("userId") Long userId);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.order.receiver.id = :userId")
    long countByReceiverId(@Param("userId") Long userId);

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
