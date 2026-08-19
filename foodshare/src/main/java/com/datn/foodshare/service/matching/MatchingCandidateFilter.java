package com.datn.foodshare.service.matching;

import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.repository.UserRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchingCandidateFilter {

    private static final Set<Role> RECEIVER_ROLES = EnumSet.of(Role.RECIPIENT, Role.ORGANIZATION);
    private static final double DEFAULT_MAX_DISTANCE_KM = 10.0;
    private static final int DEFAULT_MAX_ACTIVE_ORDERS = 2;

    private final UserRepository userRepository;
    private final ReceiverCapacityService receiverCapacityService;

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
        Map<Long, Long> activeOrderCounts = receiverCapacityService.countActiveOrders(candidateIds);

        List<User> capacityFiltered = pickupFiltered.stream()
                .filter(u -> activeOrderCounts.getOrDefault(u.getId(), 0L) < maxActiveOrders)
                .toList();

        log.debug("Candidate filter for FoodPost {}: {} receivers -> {} basic -> {} distance -> {} capacity",
                foodPost.getId(), allReceivers.size(), basicFiltered.size(),
                distanceFiltered.size(), capacityFiltered.size());

        return capacityFiltered;
    }

    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        return MatchingMetrics.distanceKm(lat1, lon1, lat2, lon2);
    }
}
