package com.datn.foodshare.service.matching;

import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.repository.FoodPostRepository;
import com.datn.foodshare.service.matching.FoodPostPriorityQueue.FoodPostPriorityEntry;
import com.datn.foodshare.service.matching.MatchingPipelineService.AllocationPlan;
import com.datn.foodshare.service.matching.MatchingPipelineService.FoodPostRecommendation;
import com.datn.foodshare.service.matching.MatchingScoreCalculator.MatchingScoreResult;
import com.datn.foodshare.service.matching.MinimumCostMaximumFlowService.AllocationResult;
import com.datn.foodshare.util.constant.PostStatus;
import com.datn.foodshare.util.constant.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchingPipelineServiceTest {

    @Mock
    private FoodPostPriorityQueue foodPostPriorityQueue;

    @Mock
    private FoodPostRepository foodPostRepository;

    @Mock
    private MatchingCandidateFilter matchingCandidateFilter;

    @Mock
    private TopKMatchingService topKMatchingService;

    @Mock
    private MinimumCostMaximumFlowService minimumCostMaximumFlowService;

    @InjectMocks
    private MatchingPipelineService service;

    @Test
    void recommend_runsFullPipelineInPriorityOrderAndRechecksDatabaseAvailability() {
        FoodPost expired = post(1L, 5, Instant.parse("2020-01-01T00:00:00Z"));
        FoodPost firstAvailable = post(2L, 4, Instant.parse("2030-01-01T00:00:00Z"));
        FoodPost secondAvailable = post(3L, 3, Instant.parse("2030-01-01T00:00:00Z"));
        User candidate = candidate(101L);
        List<FoodPostPriorityEntry> entries = List.of(
                entry(expired, 10),
                entry(firstAvailable, 20),
                entry(secondAvailable, 30)
        );

        when(foodPostPriorityQueue.getOrderedEntries()).thenReturn(entries);
        when(foodPostRepository.findAllByIdInForMatching(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(secondAvailable, expired, firstAvailable));
        when(matchingCandidateFilter.filterCandidates(any(FoodPost.class)))
                .thenReturn(List.of(candidate));
        when(topKMatchingService.findTopMatches(any(FoodPost.class), eq(List.of(candidate)), eq(2)))
                .thenReturn(List.of(score(candidate, 0.90)));

        List<FoodPostRecommendation> result = service.recommend(2, 2);

        assertEquals(List.of(2L, 3L), result.stream()
                .map(FoodPostRecommendation::foodPostId)
                .toList());
        assertEquals(101L, result.getFirst().candidates().getFirst().candidateId());
        assertEquals(0.90, result.getFirst().candidates().getFirst().score(), 1.0e-9);
        verify(matchingCandidateFilter, never()).filterCandidates(expired);
        verifyNoInteractions(minimumCostMaximumFlowService);
    }

    @Test
    void planAllocation_runsOptionalMcmfWithoutChangingFoodPostQuantity() {
        FoodPost foodPost = post(10L, 6, Instant.parse("2030-01-01T00:00:00Z"));
        User candidate = candidate(101L);
        MatchingScoreResult score = score(candidate, 0.80);
        AllocationResult expectedAllocation = new AllocationResult(4, 0.8, List.of());

        when(foodPostPriorityQueue.getOrderedEntries()).thenReturn(List.of(entry(foodPost, 20)));
        when(foodPostRepository.findAllByIdInForMatching(List.of(10L)))
                .thenReturn(List.of(foodPost));
        when(matchingCandidateFilter.filterCandidates(foodPost)).thenReturn(List.of(candidate));
        when(topKMatchingService.findTopMatches(foodPost, List.of(candidate), 3))
                .thenReturn(List.of(score));
        when(minimumCostMaximumFlowService.allocate(any(), eq(Map.of(101L, 4))))
                .thenReturn(expectedAllocation);

        AllocationPlan result = service.planAllocation(1, 3, Map.of(101L, 4));

        assertEquals(expectedAllocation, result.allocation());
        assertEquals(1, result.recommendations().size());
        assertEquals(6, foodPost.getAvailableQuantity());
        verify(minimumCostMaximumFlowService).allocate(any(), eq(Map.of(101L, 4)));
    }

    @Test
    void recommend_rejectsInvalidLimitsBeforeReadingRuntimeState() {
        assertThrows(IllegalArgumentException.class, () -> service.recommend(0, 3));
        assertThrows(IllegalArgumentException.class, () -> service.recommend(3, 0));

        verifyNoInteractions(foodPostPriorityQueue, foodPostRepository);
    }

    @Test
    void recommend_completesExperimentalDatasetWithinBudget() {
        int postCount = 1_000;
        Instant expiresAt = Instant.parse("2030-01-01T00:00:00Z");
        User candidate = candidate(101L);
        List<FoodPost> posts = new ArrayList<>(postCount);
        List<FoodPostPriorityEntry> entries = new ArrayList<>(postCount);
        for (int index = 1; index <= postCount; index++) {
            FoodPost foodPost = post(index, 10, expiresAt);
            posts.add(foodPost);
            entries.add(entry(foodPost, index));
        }

        when(foodPostPriorityQueue.getOrderedEntries()).thenReturn(entries);
        when(foodPostRepository.findAllByIdInForMatching(any())).thenReturn(posts);
        when(matchingCandidateFilter.filterCandidates(any(FoodPost.class)))
                .thenReturn(List.of(candidate));
        when(topKMatchingService.findTopMatches(any(FoodPost.class), any(), anyInt()))
                .thenReturn(List.of(score(candidate, 0.80)));

        List<FoodPostRecommendation> result = assertTimeout(
                Duration.ofSeconds(2),
                () -> service.recommend(postCount, 5)
        );

        assertEquals(postCount, result.size());
    }

    private static FoodPost post(long id, int availableQuantity, Instant expiresAt) {
        FoodPost foodPost = new FoodPost();
        foodPost.setId(id);
        foodPost.setAvailableQuantity(availableQuantity);
        foodPost.setPostStatus(PostStatus.AVAILABLE);
        foodPost.setExpiresAt(expiresAt);
        return foodPost;
    }

    private static FoodPostPriorityEntry entry(FoodPost foodPost, long remainingSeconds) {
        return new FoodPostPriorityEntry(
                foodPost.getId(),
                remainingSeconds,
                foodPost.getAvailableQuantity(),
                foodPost.getExpiresAt(),
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    private static User candidate(long id) {
        User user = new User();
        user.setId(id);
        user.setFullName("Candidate " + id);
        user.setRole(Role.RECIPIENT);
        return user;
    }

    private static MatchingScoreResult score(User candidate, double score) {
        return new MatchingScoreResult(candidate, score, 1.5, 0.4, 0.7, 0.6, 1);
    }
}
