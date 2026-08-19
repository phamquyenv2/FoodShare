package com.datn.foodshare.service.matching;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MatchingScoreCalculator {

    private final ReceiverCapacityService receiverCapacityService;

    public List<MatchingScoreResult> calculateScores(FoodPost foodPost, List<User> candidates) {
        return calculateScores(foodPost, candidates, Instant.now());
    }

    List<MatchingScoreResult> calculateScores(
            FoodPost foodPost,
            List<User> candidates,
            Instant evaluatedAt
    ) {
        Objects.requireNonNull(foodPost, "Bài đăng thực phẩm không được rỗng");
        Objects.requireNonNull(candidates, "Danh sách ứng viên không được rỗng");
        Objects.requireNonNull(evaluatedAt, "Thời điểm đánh giá không được rỗng");

        if (candidates.isEmpty()) {
            return List.of();
        }

        List<Long> receiverIds = candidates.stream()
                .map(User::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, Long> activeOrderCounts = receiverCapacityService.countActiveOrders(receiverIds);

        return candidates.stream()
                .map(candidate -> calculateScore(
                        foodPost,
                        candidate,
                        activeOrderCounts.getOrDefault(candidate.getId(), 0L),
                        evaluatedAt
                ))
                .toList();
    }

    MatchingScoreResult calculateScore(
            FoodPost foodPost,
            User candidate,
            long activeOrderCount,
            Instant evaluatedAt
    ) {
        Objects.requireNonNull(foodPost, "Bài đăng thực phẩm không được rỗng");
        Objects.requireNonNull(candidate, "Ứng viên không được rỗng");
        Objects.requireNonNull(evaluatedAt, "Thời điểm đánh giá không được rỗng");
        if (activeOrderCount < 0) {
            throw new IllegalArgumentException("Số lượng đơn hàng đang hoạt động không được âm");
        }
        if (foodPost.getExpiresAt() == null) {
            throw new IllegalArgumentException("Bài đăng thực phẩm không được rỗng");
        }

        Double distanceKm = calculateDistanceKm(foodPost, candidate);
        Double distanceScore = distanceKm == null
                ? null
                : MatchingMetrics.inverseNormalize(distanceKm);
        double urgencyScore = MatchingMetrics.urgency(foodPost.getExpiresAt(), evaluatedAt);
        double capacityScore = MatchingMetrics.inverseNormalize(activeOrderCount);

        List<Double> availableComponents = new ArrayList<>();
        if (distanceScore != null) {
            availableComponents.add(distanceScore);
        }
        availableComponents.add(urgencyScore);
        availableComponents.add(capacityScore);

        double score = availableComponents.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        return new MatchingScoreResult(
                candidate,
                score,
                distanceKm,
                distanceScore,
                urgencyScore,
                capacityScore,
                activeOrderCount
        );
    }

    private Double calculateDistanceKm(FoodPost foodPost, User candidate) {
        if (foodPost.getBusinessProfile() == null
                || foodPost.getBusinessProfile().getUser() == null) {
            return null;
        }

        User supplier = foodPost.getBusinessProfile().getUser();
        BigDecimal supplierLat = supplier.getLatitude();
        BigDecimal supplierLng = supplier.getLongitude();
        BigDecimal candidateLat = candidate.getLatitude();
        BigDecimal candidateLng = candidate.getLongitude();

        if (supplierLat == null || supplierLng == null || candidateLat == null || candidateLng == null) {
            return null;
        }

        return MatchingMetrics.distanceKm(
                supplierLat.doubleValue(),
                supplierLng.doubleValue(),
                candidateLat.doubleValue(),
                candidateLng.doubleValue()
        );
    }

    public record MatchingScoreResult(
            User candidate,
            double score,
            Double distanceKm,
            Double distanceScore,
            double urgencyScore,
            double capacityScore,
            long activeOrderCount
    ) implements Comparable<MatchingScoreResult> {

        @Override
        public int compareTo(MatchingScoreResult other) {
            int scoreComparison = Double.compare(other.score, score);
            if (scoreComparison != 0) {
                return scoreComparison;
            }

            int distanceComparison = compareNullableDistance(distanceKm, other.distanceKm);
            if (distanceComparison != 0) {
                return distanceComparison;
            }

            return compareCandidateIds(candidate.getId(), other.candidate.getId());
        }

        private static int compareNullableDistance(Double first, Double second) {
            if (first == null && second == null) {
                return 0;
            }
            if (first == null) {
                return 1;
            }
            if (second == null) {
                return -1;
            }
            return Double.compare(first, second);
        }

        private static int compareCandidateIds(Long first, Long second) {
            if (first == null && second == null) {
                return 0;
            }
            if (first == null) {
                return 1;
            }
            if (second == null) {
                return -1;
            }
            return Long.compare(first, second);
        }
    }
}
