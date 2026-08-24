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
    
    Page<Order> findByBusinessProfileId(Long businessProfileId, Pageable pageable);

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
}
