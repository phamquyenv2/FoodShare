package com.datn.foodshare.repository;

import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.util.constant.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("""
            SELECT o.receiver.id, COUNT(o)
            FROM Order o
            WHERE o.receiver.id IN :receiverIds
              AND o.orderStatus IN :statuses
            GROUP BY o.receiver.id
            """)
    List<Object[]> countActiveOrdersByReceiverIds(
            @Param("receiverIds") Collection<Long> receiverIds,
            @Param("statuses") Collection<OrderStatus> statuses);
}
