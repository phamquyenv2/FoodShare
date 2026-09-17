package com.datn.foodshare.service.matching;

import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.response.AdaptiveTopKResponse;
import com.datn.foodshare.domain.response.FoodPostResponse;
import com.datn.foodshare.repository.FoodPostRepository;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.service.matching.FoodPostPriorityQueue.FoodPostPriorityEntry;
import com.datn.foodshare.service.matching.MatchingScoreCalculator.MatchingScoreResult;
import com.datn.foodshare.service.matching.MinimumCostMaximumFlowService.AllocationResult;
import com.datn.foodshare.service.matching.MinimumCostMaximumFlowService.TopKCandidateSet;
import com.datn.foodshare.util.constant.Role;
import com.datn.foodshare.util.SecurityUtil;
import com.datn.foodshare.util.error.BusinessException;
import com.datn.foodshare.util.error.PermissionException;
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

    private final DynamicMatchingGraph graph;
    private final FoodPostRepository posts;
    private final TopKMatchingService topKMatchingService;
    private final IncrementalAllocationService incrementalAllocationService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<FoodPostResponse> recommendForCurrentUser(int maximumFoodPosts) throws PermissionException {
        return recommendForCurrentUser(maximumFoodPosts, RecommendationMode.BEST_MATCH);
    }

    @Transactional(readOnly = true)
    public List<FoodPostResponse> recommendForCurrentUser(int maximumFoodPosts, RecommendationMode mode) throws PermissionException {
        if (maximumFoodPosts <= 0 || maximumFoodPosts > 50) {
            throw new BusinessException("Số lượng gợi ý phải nằm trong khoảng từ 1 đến 50");
        }

        Long currentUserId = SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new PermissionException("Chưa đăng nhập"));
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException("Người dùng không tồn tại"));

        Instant evaluatedAt = Instant.now();
        var snapshot = graph.snapshot(evaluatedAt);

        List<DynamicMatchingGraph.WeightedPost> matchingPosts = new ArrayList<>();
        for (var p : snapshot.posts()) {
            var userScore = p.scores().stream()
                    .filter(s -> Objects.equals(s.candidate().getId(), currentUser.getId()))
                    .findFirst();
            if (userScore.isPresent()) {
                matchingPosts.add(new DynamicMatchingGraph.WeightedPost(p.post(), List.of(userScore.get())));
            }
        }

        var ranked = rankForCurrentUser(matchingPosts, mode);
        var postIds = ranked.stream().limit(maximumFoodPosts).map(p -> p.post().id()).toList();
        if (postIds.isEmpty()) {
            return List.of();
        }

        Map<Long, FoodPost> postEntities = new HashMap<>();
        posts.findAllByIdInForMatching(postIds).forEach(p -> postEntities.put(p.getId(), p));
        return ranked.stream().limit(maximumFoodPosts).map(p -> {
            FoodPost entity = postEntities.get(p.post().id());
            if (entity == null) {
                return null;
            }
            var score = p.scores().getFirst();
            return FoodPostResponse.from(entity, score.distanceKm(), score.score() * 100.0);
        }).filter(Objects::nonNull).toList();
    }

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
        Instant evaluatedAt = Instant.now();
        validateLimits(maximumFoodPosts, topK);
        var posts = graph.snapshot(evaluatedAt).posts().stream().limit(maximumFoodPosts).toList();
        var fullSets = posts.stream().map(item -> new TopKCandidateSet(item.post().toFoodPost(), item.scores())).toList();
        var adaptive = new AdaptiveTopKAllocator(incrementalAllocationService)
                .allocate(fullSets, recipientCapacities, topK, evaluatedAt);
        var recommendations = adaptive.candidateSets().stream().map(set -> new PreparedRecommendation(
                set.foodPost(), FoodPostPriorityEntry.fromFoodPost(set.foodPost(), evaluatedAt),
                set.topCandidates()).toResponse()).toList();
        return new AllocationPlan(recommendations, adaptive.allocation(), adaptive.diagnostics());
    }

    List<PreparedRecommendation> execute(int maximumFoodPosts, int topK, Instant evaluatedAt) {
        validateLimits(maximumFoodPosts, topK);
        Objects.requireNonNull(evaluatedAt, "Thời gian đánh giá không được null");

        var snapshot = graph.snapshot(evaluatedAt);
        return snapshot.posts().stream().limit(maximumFoodPosts).map(item -> {
            FoodPost post = item.post().toFoodPost();
            FoodPostPriorityEntry priority = FoodPostPriorityEntry.fromFoodPost(post, evaluatedAt);
            return new PreparedRecommendation(post, priority,
                    topKMatchingService.selectTopK(item.scores(), topK));
        }).toList();
    }

    public enum RecommendationMode { BEST_MATCH, URGENT }

    static List<DynamicMatchingGraph.WeightedPost> rankForCurrentUser(
            List<DynamicMatchingGraph.WeightedPost> posts, RecommendationMode mode) {
        var eligible = posts.stream().filter(p -> !p.scores().isEmpty());
        if (mode == RecommendationMode.URGENT) {
            return eligible.sorted(java.util.Comparator
                    .comparing((DynamicMatchingGraph.WeightedPost p) -> p.post().expiresAt())
                    .thenComparing(java.util.Comparator.comparingInt((DynamicMatchingGraph.WeightedPost p) -> p.post().availableQuantity()).reversed())
                    .thenComparingLong(p -> p.post().id())).toList();
        }
        return eligible.sorted(java.util.Comparator
                .comparingDouble((DynamicMatchingGraph.WeightedPost p) -> {
                    Double d = p.scores().getFirst().distanceKm();
                    return d != null ? d : Double.MAX_VALUE;
                })
                .thenComparing(java.util.Comparator.comparingInt((DynamicMatchingGraph.WeightedPost p) -> p.post().availableQuantity()).reversed())
                .thenComparing((DynamicMatchingGraph.WeightedPost p) -> p.scores().getFirst())
                .thenComparing(p -> p.post().expiresAt(), java.util.Comparator.reverseOrder())
                .thenComparingLong(p -> p.post().id())).toList();
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
            AllocationResult allocation,
            AdaptiveTopKResponse.Diagnostics diagnostics
    ) {
        public AllocationPlan(List<FoodPostRecommendation> recommendations, AllocationResult allocation) {
            this(recommendations, allocation, null);
        }
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
