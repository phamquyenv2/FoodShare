package com.datn.foodshare.service.matching;

import com.datn.foodshare.repository.OrderRepository;
import com.datn.foodshare.util.constant.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Instant;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class ReceiverCapacityService {

    private static final Set<OrderStatus> ACTIVE_ORDER_STATUSES = EnumSet.of(
            OrderStatus.PENDING, OrderStatus.ACCEPTED, OrderStatus.READY_FOR_PICKUP);

    private final OrderRepository orderRepository;

    Map<Long, Set<Long>> previouslyRequestedPosts(Collection<Long> receiverIds) {
        if (receiverIds.isEmpty()) return Map.of();
        Map<Long, Set<Long>> result = new java.util.HashMap<>();
        for (Object[] row : orderRepository.findPreviouslyRequestedPostIds(receiverIds)) {
            result.computeIfAbsent((Long) row[0], ignored -> new java.util.HashSet<>()).add((Long) row[1]);
        }
        return result;
    }

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

    long countFreeQuantityToday(Long receiverId) {
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate today = LocalDate.now(zone);
        Instant from = today.atStartOfDay(zone).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(zone).toInstant();
        return orderRepository.sumFreeQuantityByReceiverBetween(receiverId, from, to);
    }
}
