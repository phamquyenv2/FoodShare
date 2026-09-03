package com.datn.foodshare.repository;

import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.util.constant.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

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

    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.businessProfile.id = :businessProfileId AND o.orderStatus = :orderStatus")
    java.math.BigDecimal sumTotalAmountByBusinessProfileIdAndCompletedStatus(@Param("businessProfileId") Long businessProfileId, @Param("orderStatus") com.datn.foodshare.util.constant.OrderStatus orderStatus);

    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.businessProfile.id = :businessProfileId AND EXISTS (SELECT 1 FROM Payment p WHERE p.order = o AND p.paymentStatus = :paymentStatus)")
    java.math.BigDecimal sumTotalAmountByBusinessProfileIdAndPaymentStatus(@Param("businessProfileId") Long businessProfileId, @Param("paymentStatus") com.datn.foodshare.util.constant.TransactionStatus paymentStatus);
}
