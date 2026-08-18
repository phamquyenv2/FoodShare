package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.repository.OrderRepository;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.util.constant.OrderStatus;
import com.datn.foodshare.util.constant.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchingCandidateFilter {

    private static final Set<Role> RECEIVER_ROLES = EnumSet.of(Role.RECIPIENT, Role.ORGANIZATION);
    private static final Set<OrderStatus> ACTIVE_ORDER_STATUSES = EnumSet.of(
            OrderStatus.PENDING, OrderStatus.ACCEPTED, OrderStatus.READY_FOR_PICKUP);
    private static final double DEFAULT_MAX_DISTANCE_KM = 10.0;
    private static final int DEFAULT_MAX_ACTIVE_ORDERS = 5;
    private static final double EARTH_RADIUS_KM = 6371.0;

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    public List<User> filterCandidates(FoodPost foodPost) {
        return filterCandidates(foodPost, DEFAULT_MAX_DISTANCE_KM, DEFAULT_MAX_ACTIVE_ORDERS);
    }

    public List<User> filterCandidates(FoodPost foodPost, double maxDistanceKm, int maxActiveOrders) {
        User supplier = foodPost.getBusinessProfile().getUser();
        BigDecimal supplierLat = supplier.getLatitude();
        BigDecimal supplierLng = supplier.getLongitude();

        List<User> allReceivers = userRepository.findByRoleIn(RECEIVER_ROLES);

        List<User> basicFiltered = allReceivers.stream()
                .filter(User::isActive)
                .filter(User::isProfileCompleted)
                .filter(u -> !u.getId().equals(supplier.getId()))
                .filter(u -> u.getLatitude() != null && u.getLongitude() != null)
                .toList();

        List<User> distanceFiltered;
        if (supplierLat != null && supplierLng != null) {
            distanceFiltered = basicFiltered.stream()
                    .filter(u -> haversineKm(
                            supplierLat.doubleValue(), supplierLng.doubleValue(),
                            u.getLatitude().doubleValue(), u.getLongitude().doubleValue()
                    ) <= maxDistanceKm)
                    .toList();
        } else {
            distanceFiltered = basicFiltered;
        }

        List<User> pickupFiltered;
        Instant now = Instant.now();
        if (foodPost.getPickupEndAt() != null && !foodPost.getPickupEndAt().isAfter(now)) {
            pickupFiltered = List.of();
        } else {
            pickupFiltered = distanceFiltered;
        }

        if (pickupFiltered.isEmpty()) {
            return pickupFiltered;
        }

        List<Long> candidateIds = pickupFiltered.stream().map(User::getId).toList();
        Map<Long, Long> activeOrderCounts = countActiveOrders(candidateIds);

        List<User> capacityFiltered = pickupFiltered.stream()
                .filter(u -> activeOrderCounts.getOrDefault(u.getId(), 0L) < maxActiveOrders)
                .toList();

        log.debug("Candidate filter for FoodPost {}: {} receivers -> {} basic -> {} distance -> {} capacity",
                foodPost.getId(), allReceivers.size(), basicFiltered.size(),
                distanceFiltered.size(), capacityFiltered.size());

        return capacityFiltered;
    }

    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    private Map<Long, Long> countActiveOrders(List<Long> receiverIds) {
        return orderRepository.countActiveOrdersByReceiverIds(receiverIds, ACTIVE_ORDER_STATUSES)
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));
    }
}
