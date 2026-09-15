package com.datn.foodshare.repository;

import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.util.constant.OrderStatus;
import com.datn.foodshare.util.constant.ReportReferenceType;
import com.datn.foodshare.util.constant.ReportStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END FROM Order o JOIN o.orderDetails od WHERE o.receiver.id = :receiverId AND od.foodPost.id = :foodPostId")
    boolean existsByReceiverAndFoodPost(@Param("receiverId") Long receiverId, @Param("foodPostId") Long foodPostId);

    @Query("""
            SELECT o.receiver.id, COUNT(o)
            FROM Order o
            WHERE o.receiver.id IN :receiverIds
              AND o.orderStatus IN :statuses
            GROUP BY o.receiver.id
            """)
    List<Object[]> countActiveOrdersByReceiverIds(@Param("receiverIds") Collection<Long> receiverIds, @Param("statuses") Collection<OrderStatus> statuses);
    Page<Order> findByReceiverId(Long receiverId, Pageable pageable);
    
    @Query("""
            SELECT o FROM Order o
            LEFT JOIN o.receiver r
            WHERE o.businessProfile.id = :businessProfileId
              AND (:status IS NULL OR o.orderStatus = :status)
              AND (:keyword IS NULL OR LOWER(o.orderCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(r.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Order> searchSupplierOrders(
            @Param("businessProfileId") Long businessProfileId, 
            @Param("status") OrderStatus status, 
            @Param("keyword") String keyword, 
            Pageable pageable);

    @Query("""
            SELECT DISTINCT o FROM Order o
            LEFT JOIN FETCH o.orderDetails od
            LEFT JOIN FETCH od.foodPost fp
            JOIN FETCH o.receiver r
            JOIN FETCH o.businessProfile bp
            WHERE o.id IN :ids
            """)
    List<Order> findAllWithDetailsByIdIn(@Param("ids") Collection<Long> ids);

    @Query("""
            SELECT o FROM Order o
            LEFT JOIN FETCH o.orderDetails od
            LEFT JOIN FETCH od.foodPost fp
            JOIN FETCH o.receiver r
            JOIN FETCH o.businessProfile bp
            WHERE o.id = :id
            """)
    Optional<Order> findByIdWithDetails(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT DISTINCT o.id FROM Order o
            JOIN o.orderDetails od
            JOIN od.foodPost fp
            WHERE o.orderStatus IN :statuses
              AND (
                  (o.pickupDeadline IS NOT NULL AND o.pickupDeadline < :now)
                  OR fp.pickupEndAt < :now
                  OR fp.expiresAt <= :now
              )
            """)
    List<Long> findIdsPastFulfillmentWindow(
            @Param("statuses") Collection<OrderStatus> statuses,
            @Param("now") Instant now);

    @Query("""
            SELECT DISTINCT o FROM Order o
            JOIN FETCH o.receiver r
            WHERE o.orderStatus = :status
              AND o.pickupDeadline IS NOT NULL
              AND o.pickupDeadline >= :from
              AND o.pickupDeadline < :to
            """)
    List<Order> findReadyOrdersForPickupReminder(
            @Param("status") OrderStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            SELECT o.id FROM Order o
            WHERE o.orderStatus = :status
              AND o.deliveredAt IS NOT NULL
              AND o.deliveredAt <= :deliveredBefore
              AND NOT EXISTS (
                  SELECT r.id FROM Report r
                  WHERE r.referenceType = :referenceType
                    AND r.referenceId = o.id
                    AND r.reportStatus IN :activeReportStatuses
              )
            """)
    List<Long> findIdsEligibleForAutoCompletion(
            @Param("status") OrderStatus status,
            @Param("deliveredBefore") Instant deliveredBefore,
            @Param("referenceType") ReportReferenceType referenceType,
            @Param("activeReportStatuses") Collection<ReportStatus> activeReportStatuses);

    @Query("""
            SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
            FROM Report r
            WHERE r.referenceType = :referenceType
              AND r.referenceId = :orderId
              AND r.reportStatus IN :activeReportStatuses
            """)
    boolean hasActiveReport(
            @Param("orderId") Long orderId,
            @Param("referenceType") ReportReferenceType referenceType,
            @Param("activeReportStatuses") Collection<ReportStatus> activeReportStatuses);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.payments WHERE o.id IN :ids")
    List<Order> findAllWithPaymentsByIdIn(@Param("ids") Collection<Long> ids);

    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.businessProfile.id = :businessProfileId AND o.orderStatus = :orderStatus")
    java.math.BigDecimal sumTotalAmountByBusinessProfileIdAndCompletedStatus(@Param("businessProfileId") Long businessProfileId, @Param("orderStatus") com.datn.foodshare.util.constant.OrderStatus orderStatus);

    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.businessProfile.id = :businessProfileId AND EXISTS (SELECT 1 FROM Payment p WHERE p.order = o AND p.paymentStatus = :paymentStatus)")
    java.math.BigDecimal sumTotalAmountByBusinessProfileIdAndPaymentStatus(@Param("businessProfileId") Long businessProfileId, @Param("paymentStatus") com.datn.foodshare.util.constant.TransactionStatus paymentStatus);
}
