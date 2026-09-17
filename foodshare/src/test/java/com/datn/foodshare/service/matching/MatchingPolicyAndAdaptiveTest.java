package com.datn.foodshare.service.matching;

import com.datn.foodshare.domain.entity.*;
import com.datn.foodshare.service.matching.MinimumCostMaximumFlowService.*;
import com.datn.foodshare.service.matching.MatchingScoreCalculator.MatchingScoreResult;
import com.datn.foodshare.util.constant.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MatchingPolicyAndAdaptiveTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private final IncrementalAllocationService engine = new IncrementalAllocationService(new MinimumCostMaximumFlowService());
    private final AdaptiveTopKAllocator adaptive = new AdaptiveTopKAllocator(engine);

    static MatchingScoreResult edge(long id, Role role, double score, long active) {
        return new MatchingScoreResult(User.builder().id(id).role(role).build(), score, 1.0, .5, .5, .5, active);
    }
    static TopKCandidateSet post(long id, int quantity, MatchingScoreResult... scores) {
        return new TopKCandidateSet(DynamicWeightedMatchingTest.post(id, quantity), List.of(scores));
    }
    @Test void personalRecipientGetsOnePerPostAndOnlyRemainingSlots() {
        var e = edge(20, Role.RECIPIENT, .9, 1);
        var result = adaptive.allocate(List.of(post(10, 10, e), post(11, 10, e)), Map.of(20L, 100), 1, NOW);
        assertEquals(1, result.allocation().maximumFlow());
        assertTrue(result.allocation().allocations().stream().allMatch(a -> a.quantity() <= 1));
    }
    @Test void personalRecipientWithNoActiveOrdersCanReceiveTwoDifferentPosts() {
        var e = edge(20, Role.RECIPIENT, .9, 0);
        assertEquals(2, adaptive.allocate(List.of(post(10, 10, e), post(11, 10, e)),
                Map.of(), 1, NOW).allocation().maximumFlow());
        assertEquals(1, adaptive.allocate(List.of(post(10, 10, e)), Map.of(), 1, NOW).allocation().maximumFlow());
    }
    @Test void organizationCapacityIsInPortionsAndExhaustedReceiverIsExcluded() {
        assertEquals(7, adaptive.allocate(List.of(post(10, 10, edge(20, Role.ORGANIZATION, .9, 1))),
                Map.of(20L, 7), 1, NOW).allocation().maximumFlow());
        assertEquals(0, adaptive.allocate(List.of(post(10, 10, edge(20, Role.ORGANIZATION, .9, 2))),
                Map.of(20L, 7), 1, NOW).allocation().maximumFlow());
    }
    @Test void cacheIsInvalidatedWhenRoleOrActiveOrderCountChanges() {
        engine.allocate(List.of(post(10, 10, edge(20, Role.ORGANIZATION, .9, 0))), Map.of(20L, 7), NOW);
        assertEquals(1, engine.allocate(List.of(post(10, 10, edge(20, Role.RECIPIENT, .9, 0))),
                Map.of(20L, 7), NOW).maximumFlow());
        assertEquals(0, engine.allocate(List.of(post(10, 10, edge(20, Role.RECIPIENT, .9, 2))),
                Map.of(20L, 7), NOW).maximumFlow());
    }
    @Test void expandsKWhenSharedFirstChoiceLosesMaximumFlow() {
        var a = edge(20, Role.ORGANIZATION, .9, 0);
        var b = edge(21, Role.ORGANIZATION, .8, 0);
        var result = adaptive.allocate(List.of(post(10, 1, a, b), post(11, 1, a, b)),
                Map.of(20L, 1, 21L, 1), 1, NOW);
        assertEquals(2, result.allocation().maximumFlow());
        assertEquals(2, result.diagnostics().effectiveK());
        assertEquals(1, result.diagnostics().attempts().getFirst().flow());
        assertTrue(result.diagnostics().certified());
    }
    @Test void equalFlowIsNotEnoughWhenPrunedGraphHasHigherCost() {
        var result = adaptive.allocate(List.of(
                post(10, 1, edge(20, Role.ORGANIZATION, .9, 0),
                        edge(22, Role.ORGANIZATION, .89, 0), edge(21, Role.ORGANIZATION, .88, 0)),
                post(11, 1, edge(20, Role.ORGANIZATION, .87, 0), edge(23, Role.ORGANIZATION, .1, 0))),
                Map.of(20L, 1, 21L, 1, 22L, 0, 23L, 1), 2, NOW);
        assertEquals(2, result.diagnostics().attempts().getFirst().flow());
        assertEquals(2, result.allocation().maximumFlow());
        assertEquals(.25, result.allocation().minimumCost(), 1e-8);
        assertEquals(3, result.diagnostics().effectiveK());
    }
    @Test void retainsSmallKWhenAlreadyEquivalentToFullGraph() {
        var result = adaptive.allocate(List.of(post(10, 1,
                edge(20, Role.ORGANIZATION, .9, 0), edge(21, Role.ORGANIZATION, .8, 0))),
                Map.of(), 1, NOW);
        assertEquals(1, result.diagnostics().effectiveK());
        assertEquals(.1, result.allocation().minimumCost(), 1e-8);
    }
    @Test void historicalRequestsExcludeOnlyIndividualsAndPersistAcrossUpdates() {
        DynamicMatchingGraph graph = new DynamicMatchingGraph();
        User receiver = DynamicWeightedMatchingTest.candidate(20, "10.7815");
        var p = DynamicWeightedMatchingTest.post(10, 5);
        graph.rebuild(List.of(p), List.of(receiver), Map.of(), Map.of(20L, Set.of(10L)));
        assertTrue(graph.snapshotForCandidate(20, NOW).posts().getFirst().scores().isEmpty());
        graph.synchronizeCandidate(receiver, 0, Set.of(10L));
        assertTrue(graph.snapshotForCandidate(20, NOW).posts().getFirst().scores().isEmpty());
        receiver.setRole(Role.ORGANIZATION);
        graph.synchronizeCandidate(receiver, 0, Set.of(10L));
        assertFalse(graph.snapshotForCandidate(20, NOW).posts().getFirst().scores().isEmpty());
    }
    @Test void bestMatchSortsByScoreWhileUrgentKeepsExpiryOrder() {
        var first = new DynamicMatchingGraph.WeightedPost(
                DynamicMatchingGraph.FoodPostNode.from(DynamicWeightedMatchingTest.post(10, 1)),
                List.of(edge(20, Role.RECIPIENT, .4, 0)));
        var second = new DynamicMatchingGraph.WeightedPost(
                DynamicMatchingGraph.FoodPostNode.from(DynamicWeightedMatchingTest.post(11, 1)),
                List.of(edge(20, Role.RECIPIENT, .9, 0)));
        assertEquals(11, MatchingPipelineService.rankForCurrentUser(List.of(first, second),
                MatchingPipelineService.RecommendationMode.BEST_MATCH).getFirst().post().id());
        assertEquals(10, MatchingPipelineService.rankForCurrentUser(List.of(first, second),
                MatchingPipelineService.RecommendationMode.URGENT).getFirst().post().id());
    }
    @Test void randomizedAdaptivePlansAgreeWithAllEdgesUnderRoleConstraints() {
        Random random = new Random(7331);
        for (int iteration = 0; iteration < 80; iteration++) {
            List<TopKCandidateSet> sets = new ArrayList<>();
            for (long id = 10; id < 14; id++) {
                List<MatchingScoreResult> edges = new ArrayList<>();
                for (long receiver = 20; receiver < 24; receiver++) if (random.nextBoolean())
                    edges.add(edge(receiver, receiver % 2 == 0 ? Role.RECIPIENT : Role.ORGANIZATION,
                            random.nextInt(101) / 100.0, receiver % 3));
                sets.add(post(id, random.nextInt(4), edges.toArray(MatchingScoreResult[]::new)));
            }
            var capacities = Map.of(20L, 5, 21L, 3, 22L, 1, 23L, 4);
            var expected = new MinimumCostMaximumFlowService().allocate(sets, capacities, NOW);
            var actual = adaptive.allocate(sets, capacities, 1, NOW).allocation();
            assertEquals(expected.maximumFlow(), actual.maximumFlow());
            assertEquals(expected.minimumCost(), actual.minimumCost(), 1e-8);
        }
    }
}
