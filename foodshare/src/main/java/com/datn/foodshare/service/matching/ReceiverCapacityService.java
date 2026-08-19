package com.datn.foodshare.service.matching;

import com.datn.foodshare.repository.OrderRepository;
import com.datn.foodshare.util.constant.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class ReceiverCapacityService {

    private static final Set<OrderStatus> ACTIVE_ORDER_STATUSES = EnumSet.of(
            OrderStatus.PENDING, OrderStatus.ACCEPTED, OrderStatus.READY_FOR_PICKUP);

    private final OrderRepository orderRepository;

    Map<Long, Long> countActiveOrders(Collection<Long> receiverIds) {
        if (receiverIds.isEmpty()) {
            return Map.of();
        }

        return orderRepository.countActiveOrdersByReceiverIds(receiverIds, ACTIVE_ORDER_STATUSES)
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));
    }
}
