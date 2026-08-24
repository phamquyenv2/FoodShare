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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchingCandidateFilter {

    static final Set<Role> RECEIVER_ROLES = EnumSet.of(Role.RECIPIENT, Role.ORGANIZATION);
    private static final double DEFAULT_MAX_DISTANCE_KM = 10.0;
    private static final int DEFAULT_MAX_ACTIVE_ORDERS = 2;

    private final UserRepository userRepository;
    private final ReceiverCapacityService receiverCapacityService;

    public List<User> filterCandidates(FoodPost foodPost) {
        return filterCandidates(foodPost, DEFAULT_MAX_DISTANCE_KM, DEFAULT_MAX_ACTIVE_ORDERS);
    }

    public List<User> filterCandidates(FoodPost foodPost, double maxDistanceKm, int maxActiveOrders) {
        User supplier = foodPost.getBusinessProfile().getUser();
        List<User> allReceivers = userRepository.findByRoleIn(RECEIVER_ROLES);

        List<User> basicFiltered = allReceivers.stream()
                .filter(this::isGloballyEligibleCandidate)
                .filter(u -> !Objects.equals(u.getId(), supplier.getId()))
                .toList();

        Instant now = Instant.now();
        List<User> relationFiltered = basicFiltered.stream()
                .filter(candidate -> matchesPostConstraints(foodPost, candidate, maxDistanceKm, now))
                .toList();

        if (relationFiltered.isEmpty()) {
            return relationFiltered;
        }

        List<Long> candidateIds = relationFiltered.stream().map(User::getId).toList();
        Map<Long, Long> activeOrderCounts = receiverCapacityService.countActiveOrders(candidateIds);

        List<User> capacityFiltered = relationFiltered.stream()
                .filter(u -> activeOrderCounts.getOrDefault(u.getId(), 0L) < maxActiveOrders)
                .toList();

        log.debug("Bộ lọc ứng viên cho FoodPost {}: {} người nhận -> {} cơ bản -> {} khoảng cách -> {} công suất",
                foodPost.getId(), allReceivers.size(), basicFiltered.size(),
                relationFiltered.size(), capacityFiltered.size());

        return capacityFiltered;
    }

    Set<Long> findEligibleFoodPostIds(List<FoodPost> foodPosts, User candidate) {
        if (!isGloballyEligibleCandidate(candidate) || foodPosts.isEmpty()) {
            return Set.of();
        }

        long activeOrderCount = receiverCapacityService
                .countActiveOrders(List.of(candidate.getId()))
                .getOrDefault(candidate.getId(), 0L);
        if (activeOrderCount >= DEFAULT_MAX_ACTIVE_ORDERS) {
            return Set.of();
        }

        Instant now = Instant.now();
        return foodPosts.stream()
                .filter(foodPost -> matchesPostConstraints(
                        foodPost,
                        candidate,
                        DEFAULT_MAX_DISTANCE_KM,
                        now
                ))
                .map(FoodPost::getId)
                .collect(Collectors.toUnmodifiableSet());
    }

    boolean isGloballyEligibleCandidate(User candidate) {
        return candidate != null
                && candidate.getId() != null
                && RECEIVER_ROLES.contains(candidate.getRole())
                && candidate.isActive()
                && candidate.isProfileCompleted()
                && candidate.getLatitude() != null
                && candidate.getLongitude() != null;
    }

    private boolean matchesPostConstraints(
            FoodPost foodPost,
            User candidate,
            double maxDistanceKm,
            Instant evaluatedAt
    ) {
        User supplier = foodPost.getBusinessProfile().getUser();
        if (Objects.equals(candidate.getId(), supplier.getId())) {
            return false;
        }
        if (foodPost.getPickupEndAt() != null && !foodPost.getPickupEndAt().isAfter(evaluatedAt)) {
            return false;
        }

        BigDecimal supplierLat = supplier.getLatitude();
        BigDecimal supplierLng = supplier.getLongitude();
        if (supplierLat == null || supplierLng == null) {
            return true;
        }

        return haversineKm(
                supplierLat.doubleValue(), supplierLng.doubleValue(),
                candidate.getLatitude().doubleValue(), candidate.getLongitude().doubleValue()
        ) <= maxDistanceKm;
    }

    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        return MatchingMetrics.distanceKm(lat1, lon1, lat2, lon2);
    }
}
