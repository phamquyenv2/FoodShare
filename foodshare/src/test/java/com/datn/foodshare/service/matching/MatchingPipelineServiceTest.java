package com.datn.foodshare.service.matching;

import com.datn.foodshare.domain.entity.*;
import com.datn.foodshare.domain.response.FoodPostResponse;
import com.datn.foodshare.repository.*;
import com.datn.foodshare.util.SecurityUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MatchingPipelineServiceTest {
    private final DynamicMatchingGraph graph = new DynamicMatchingGraph();
    private final FoodPostRepository posts = mock(FoodPostRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final IncrementalAllocationService allocation = new IncrementalAllocationService(new MinimumCostMaximumFlowService());
    private final MatchingPipelineService pipeline = new MatchingPipelineService(graph, posts,
            new TopKMatchingService(new MatchingScoreCalculator(mock(ReceiverCapacityService.class))), allocation, users);

    @Test void recommendAndAllocateTraverseGraphWithoutRepositoryCalls() {
        var post = DynamicWeightedMatchingTest.post(10, 5);
        var receiver = DynamicWeightedMatchingTest.candidate(20, "10.7815");
        graph.rebuild(List.of(post), List.of(receiver), Map.of(20L, 1L));
        var result = pipeline.recommend(1, 2);
        assertEquals(10, result.getFirst().foodPostId());
        assertEquals(20, result.getFirst().candidates().getFirst().candidateId());
        assertEquals(1, result.getFirst().candidates().getFirst().activeOrderCount());
        assertEquals(1, pipeline.planAllocation(1, 2, Map.of(20L, 3)).allocation().maximumFlow());
        assertEquals(5, graph.getFoodPost(10).orElseThrow().availableQuantity());
        verifyNoInteractions(posts, users);
    }
    @Test void preservesUrgencyPriorityAndUsesTopKWeightedNeighbors() {
        var first = DynamicWeightedMatchingTest.post(10, 5);
        var second = DynamicWeightedMatchingTest.post(11, 5);
        first.setExpiresAt(Instant.parse("2030-01-01T01:00:00Z"));
        graph.rebuild(List.of(second, first), List.of(
                DynamicWeightedMatchingTest.candidate(20, "10.7815"),
                DynamicWeightedMatchingTest.candidate(21, "10.7769")), Map.of());
        var result = pipeline.recommend(2, 1);
        assertEquals(List.of(10L, 11L), result.stream().map(r -> r.foodPostId()).toList());
        assertEquals(21, result.getFirst().candidates().getFirst().candidateId());
    }
    @Test void invalidLimitsFailBeforeReadingGraph() {
        assertThrows(IllegalArgumentException.class, () -> pipeline.recommend(0, 1));
        assertThrows(IllegalArgumentException.class, () -> pipeline.recommend(1, 0));
        verifyNoInteractions(posts, users);
    }
    @Test void currentUserHydratesOnlySelectedGraphNeighbors() throws Exception {
        var receiver = DynamicWeightedMatchingTest.candidate(20, "10.7815");
        var post = DynamicWeightedMatchingTest.post(10, 5);
        Category category = new Category(); category.setId(3L); category.setName("Food"); post.setCategory(category);
        graph.rebuild(List.of(post), List.of(receiver), Map.of());
        when(users.findById(20L)).thenReturn(Optional.of(receiver));
        when(posts.findAllByIdInForMatching(List.of(10L))).thenReturn(List.of(post));
        try (MockedStatic<SecurityUtil> security = mockStatic(SecurityUtil.class)) {
            security.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(20L));
            List<FoodPostResponse> result = pipeline.recommendForCurrentUser(1);
            assertEquals(10, result.getFirst().getId());
            assertTrue(result.getFirst().getMatchScore() > 0);
            assertNotNull(result.getFirst().getDistanceKm());
        }
        verify(posts).findAllByIdInForMatching(List.of(10L));
        verifyNoMoreInteractions(posts);
    }
    @Test void capacityReachedReturnsNoRecommendationsAndDoesNotHydrate() throws Exception {
        var receiver = DynamicWeightedMatchingTest.candidate(20, "10.7815");
        graph.rebuild(List.of(DynamicWeightedMatchingTest.post(10, 5)), List.of(receiver), Map.of(20L, 2L));
        when(users.findById(20L)).thenReturn(Optional.of(receiver));
        try (MockedStatic<SecurityUtil> security = mockStatic(SecurityUtil.class)) {
            security.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(20L));
            assertTrue(pipeline.recommendForCurrentUser(5).isEmpty());
        }
        verifyNoInteractions(posts);
    }
    @Test void rejectsUnavailableGraphInsteadOfServingStaleRecommendations() {
        graph.markUnavailable();
        assertThrows(IllegalStateException.class, () -> pipeline.recommend(1, 1));
    }
}
