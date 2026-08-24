package com.datn.foodshare.service.matching;

import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.repository.FoodPostRepository;
import com.datn.foodshare.service.matching.FoodPostPriorityQueue.FoodPostPriorityEntry;
import com.datn.foodshare.service.matching.MatchingScoreCalculator.MatchingScoreResult;
import com.datn.foodshare.service.matching.MinimumCostMaximumFlowService.AllocationResult;
import com.datn.foodshare.service.matching.MinimumCostMaximumFlowService.TopKCandidateSet;
import com.datn.foodshare.util.constant.PostStatus;
import com.datn.foodshare.util.constant.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MatchingPipelineService {

    private final FoodPostPriorityQueue foodPostPriorityQueue;
    private final FoodPostRepository foodPostRepository;
    private final MatchingCandidateFilter matchingCandidateFilter;
    private final TopKMatchingService topKMatchingService;
    private final MinimumCostMaximumFlowService minimumCostMaximumFlowService;

    @Transactional(readOnly = true)
    public List<FoodPostRecommendation> recommend(int maximumFoodPosts, int topK) {
        return execute(maximumFoodPosts, topK, Instant.now()).stream()
                .map(PreparedRecommendation::toResponse)
                .toList();
    }
    
    @Transactional(readOnly = true)
    public AllocationPlan planAllocation(
            int maximumFoodPosts,
            int topK,
            Map<Long, Integer> recipientCapacities
    ) {
        Objects.requireNonNull(recipientCapacities, "Khả năng của người nhận không được null");
        List<PreparedRecommendation> prepared = execute(maximumFoodPosts, topK, Instant.now());
        AllocationResult allocation = minimumCostMaximumFlowService.allocate(
                prepared.stream()
                        .map(item -> new TopKCandidateSet(item.foodPost(), item.topCandidates()))
                        .toList(),
                recipientCapacities
        );
        return new AllocationPlan(
                prepared.stream().map(PreparedRecommendation::toResponse).toList(),
                allocation
        );
    }

    List<PreparedRecommendation> execute(int maximumFoodPosts, int topK, Instant evaluatedAt) {
        validateLimits(maximumFoodPosts, topK);
        Objects.requireNonNull(evaluatedAt, "Thời gian đánh giá không được null");

        List<FoodPostPriorityEntry> priorityEntries = foodPostPriorityQueue.getOrderedEntries();
        if (priorityEntries.isEmpty()) {
            return List.of();
        }

        List<Long> orderedIds = priorityEntries.stream()
                .map(FoodPostPriorityEntry::foodPostId)
                .toList();
        Map<Long, FoodPost> postsById = new HashMap<>();
        foodPostRepository.findAllByIdInForMatching(orderedIds)
                .forEach(foodPost -> postsById.put(foodPost.getId(), foodPost));

        List<PreparedRecommendation> recommendations = new ArrayList<>();
        for (FoodPostPriorityEntry entry : priorityEntries) {
            if (recommendations.size() == maximumFoodPosts) {
                break;
            }
            FoodPost foodPost = postsById.get(entry.foodPostId());
            if (!isAvailable(foodPost, evaluatedAt)) {
                continue;
            }

            List<User> candidates = matchingCandidateFilter.filterCandidates(foodPost);
            List<MatchingScoreResult> topCandidates = topKMatchingService.findTopMatches(
                    foodPost,
                    candidates,
                    topK
            );
            recommendations.add(new PreparedRecommendation(foodPost, entry, topCandidates));
        }
        return List.copyOf(recommendations);
    }

    private boolean isAvailable(FoodPost foodPost, Instant evaluatedAt) {
        return foodPost != null
                && foodPost.getPostStatus() == PostStatus.AVAILABLE
                && foodPost.getAvailableQuantity() > 0
                && foodPost.getExpiresAt() != null
                && foodPost.getExpiresAt().isAfter(evaluatedAt);
    }

    private void validateLimits(int maximumFoodPosts, int topK) {
        if (maximumFoodPosts <= 0) {
            throw new IllegalArgumentException("Số lượng FoodPost tối đa phải lớn hơn 0");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("Top-K phải lớn hơn 0");
        }
    }

    public record CandidateRecommendation(
            long candidateId,
            String fullName,
            Role role,
            double score,
            Double distanceKm,
            double urgencyScore,
            double capacityScore,
            long activeOrderCount
    ) {
        private static CandidateRecommendation from(MatchingScoreResult result) {
            User candidate = result.candidate();
            return new CandidateRecommendation(
                    candidate.getId(),
                    candidate.getFullName(),
                    candidate.getRole(),
                    result.score(),
                    result.distanceKm(),
                    result.urgencyScore(),
                    result.capacityScore(),
                    result.activeOrderCount()
            );
        }
    }

    public record FoodPostRecommendation(
            long foodPostId,
            int availableQuantity,
            long remainingSeconds,
            List<CandidateRecommendation> candidates
    ) {
        public FoodPostRecommendation {
            candidates = List.copyOf(candidates);
        }
    }

    public record AllocationPlan(
            List<FoodPostRecommendation> recommendations,
            AllocationResult allocation
    ) {
        public AllocationPlan {
            recommendations = List.copyOf(recommendations);
            Objects.requireNonNull(allocation, "Allocation không được null");
        }
    }

    record PreparedRecommendation(
            FoodPost foodPost,
            FoodPostPriorityEntry priority,
            List<MatchingScoreResult> topCandidates
    ) {
        PreparedRecommendation {
            topCandidates = List.copyOf(topCandidates);
        }

        FoodPostRecommendation toResponse() {
            List<CandidateRecommendation> candidates = topCandidates.stream()
                    .map(CandidateRecommendation::from)
                    .toList();
            return new FoodPostRecommendation(
                    foodPost.getId(),
                    foodPost.getAvailableQuantity(),
                    priority.remainingSeconds(),
                    candidates
            );
        }
    }
}
