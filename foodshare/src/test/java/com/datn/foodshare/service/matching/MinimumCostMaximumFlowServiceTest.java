package com.datn.foodshare.service.matching;

import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.service.matching.MatchingScoreCalculator.MatchingScoreResult;
import com.datn.foodshare.service.matching.MinimumCostMaximumFlowService.Allocation;
import com.datn.foodshare.service.matching.MinimumCostMaximumFlowService.AllocationResult;
import com.datn.foodshare.service.matching.MinimumCostMaximumFlowService.TopKCandidateSet;
import com.datn.foodshare.util.constant.PostStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimumCostMaximumFlowServiceTest {

    private final MinimumCostMaximumFlowService service = new MinimumCostMaximumFlowService();

    @Test
    void allocate_maximizesFlowThenUsesLowestCostTopKCandidate() {
        FoodPost post = availablePost(10L, 5);
        User bestCandidate = candidate(101L);
        User otherCandidate = candidate(102L);

        AllocationResult result = service.allocate(List.of(new TopKCandidateSet(
                post,
                List.of(score(otherCandidate, 0.60), score(bestCandidate, 0.90))
        )));

        assertEquals(5, result.maximumFlow());
        assertEquals(0.5, result.minimumCost(), 1.0e-9);
        assertEquals(1, result.allocations().size());
        Allocation allocation = result.allocations().getFirst();
        assertEquals(10L, allocation.foodPostId());
        assertEquals(101L, allocation.candidateId());
        assertEquals(5, allocation.quantity());
        assertEquals(0.10, allocation.unitCost(), 1.0e-9);
        assertEquals(0.90, allocation.score(), 1.0e-9);
    }

    @Test
    void allocate_honorsRecipientCapacitiesAndFindsMinimumCostRerouting() {
        FoodPost firstPost = availablePost(10L, 5);
        FoodPost secondPost = availablePost(20L, 4);
        User firstCandidate = candidate(101L);
        User secondCandidate = candidate(102L);

        AllocationResult result = service.allocate(
                List.of(
                        new TopKCandidateSet(firstPost, List.of(
                                score(firstCandidate, 0.90),
                                score(secondCandidate, 0.80)
                        )),
                        new TopKCandidateSet(secondPost, List.of(
                                score(firstCandidate, 0.95),
                                score(secondCandidate, 0.70)
                        ))
                ),
                Map.of(101L, 3, 102L, 4)
        );

        assertEquals(7, result.maximumFlow());
        assertEquals(0.95, result.minimumCost(), 1.0e-9);
        assertEquals(3, quantity(result, 20L, 101L));
        assertEquals(4, quantity(result, 10L, 102L));
        assertEquals(3, quantityForCandidate(result, 101L));
        assertEquals(4, quantityForCandidate(result, 102L));
    }

    @Test
    void allocate_neverAllocatesMoreThanFoodPostAvailableQuantity() {
        FoodPost firstPost = availablePost(10L, 2);
        FoodPost secondPost = availablePost(20L, 3);
        User candidate = candidate(101L);

        AllocationResult result = service.allocate(List.of(
                new TopKCandidateSet(firstPost, List.of(score(candidate, 0.90))),
                new TopKCandidateSet(secondPost, List.of(score(candidate, 0.80)))
        ));

        assertEquals(5, result.maximumFlow());
        assertEquals(2, quantityForPost(result, 10L));
        assertEquals(3, quantityForPost(result, 20L));
    }

    @Test
    void allocate_doesNotMutateFoodPostOrPersistAnything() {
        FoodPost post = availablePost(10L, 6);

        AllocationResult result = service.allocate(List.of(
                new TopKCandidateSet(post, List.of(score(candidate(101L), 0.75)))
        ));

        assertEquals(6, result.maximumFlow());
        assertEquals(6, post.getAvailableQuantity());
        assertEquals(PostStatus.AVAILABLE, post.getPostStatus());
    }

    @Test
    void allocate_skipsUnavailablePostsAndSupportsIndependentEmptyTopK() {
        FoodPost expiredPost = availablePost(10L, 4);
        expiredPost.setExpiresAt(Instant.parse("2020-01-01T00:00:00Z"));

        AllocationResult expiredResult = service.allocate(List.of(
                new TopKCandidateSet(expiredPost, List.of(score(candidate(101L), 0.90)))
        ));
        AllocationResult emptyTopKResult = service.allocate(List.of(
                new TopKCandidateSet(availablePost(20L, 4), List.of())
        ));

        assertEquals(0, expiredResult.maximumFlow());
        assertTrue(expiredResult.allocations().isEmpty());
        assertEquals(0, emptyTopKResult.maximumFlow());
        assertTrue(emptyTopKResult.allocations().isEmpty());
    }

    @Test
    void allocate_rejectsInvalidScoreAndRecipientCapacity() {
        FoodPost post = availablePost(10L, 1);
        User candidate = candidate(101L);

        assertThrows(IllegalArgumentException.class, () -> service.allocate(
                List.of(new TopKCandidateSet(post, List.of(score(candidate, 1.01))))
        ));
        assertThrows(IllegalArgumentException.class, () -> service.allocate(
                List.of(new TopKCandidateSet(post, List.of(score(candidate, 0.90)))),
                Map.of(101L, -1)
        ));
    }

    private static FoodPost availablePost(long id, int availableQuantity) {
        FoodPost post = new FoodPost();
        post.setId(id);
        post.setPostStatus(PostStatus.AVAILABLE);
        post.setAvailableQuantity(availableQuantity);
        post.setExpiresAt(Instant.parse("2030-01-01T00:00:00Z"));
        return post;
    }

    private static User candidate(long id) {
        User candidate = new User();
        candidate.setId(id);
        return candidate;
    }

    private static MatchingScoreResult score(User candidate, double score) {
        return new MatchingScoreResult(candidate, score, 1.0, 0.5, 0.5, 0.5, 0);
    }

    private static long quantity(AllocationResult result, long foodPostId, long candidateId) {
        return result.allocations().stream()
                .filter(allocation -> allocation.foodPostId() == foodPostId)
                .filter(allocation -> allocation.candidateId() == candidateId)
                .mapToLong(Allocation::quantity)
                .sum();
    }

    private static long quantityForPost(AllocationResult result, long foodPostId) {
        return result.allocations().stream()
                .filter(allocation -> allocation.foodPostId() == foodPostId)
                .mapToLong(Allocation::quantity)
                .sum();
    }

    private static long quantityForCandidate(AllocationResult result, long candidateId) {
        return result.allocations().stream()
                .filter(allocation -> allocation.candidateId() == candidateId)
                .mapToLong(Allocation::quantity)
                .sum();
    }
}
