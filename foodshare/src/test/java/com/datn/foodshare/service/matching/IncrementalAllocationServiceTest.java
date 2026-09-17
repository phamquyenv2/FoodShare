package com.datn.foodshare.service.matching;

import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.service.matching.MatchingScoreCalculator.MatchingScoreResult;
import com.datn.foodshare.service.matching.MinimumCostMaximumFlowService.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class IncrementalAllocationServiceTest {
    private final MinimumCostMaximumFlowService batch = new MinimumCostMaximumFlowService();
    private final IncrementalAllocationService incremental = new IncrementalAllocationService(batch);
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private static MatchingScoreResult edge(long receiver, double score) {
        return new MatchingScoreResult(User.builder().id(receiver).build(), score, null, null, 0, 0, 0);
    }
    private static TopKCandidateSet set(long post, int quantity, MatchingScoreResult... edges) {
        return new TopKCandidateSet(DynamicWeightedMatchingTest.post(post, quantity), List.of(edges));
    }
    private void equivalent(List<TopKCandidateSet> sets, Map<Long, Integer> capacities) {
        AllocationResult expected = batch.allocate(sets, capacities, NOW);
        AllocationResult actual = incremental.allocate(sets, capacities, NOW);
        assertEquals(expected.maximumFlow(), actual.maximumFlow());
        assertEquals(expected.minimumCost(), actual.minimumCost(), 1e-8);
        Map<Long, Long> used = new HashMap<>(), supplied = new HashMap<>();
        actual.allocations().forEach(a -> {
            used.merge(a.candidateId(), a.quantity(), Long::sum);
            supplied.merge(a.foodPostId(), a.quantity(), Long::sum);
            assertTrue(sets.stream().filter(s -> s.foodPost().getId() == a.foodPostId())
                    .flatMap(s -> s.topCandidates().stream()).anyMatch(e -> e.candidate().getId() == a.candidateId()));
        });
        used.forEach((id, qty) -> assertTrue(qty <= capacities.getOrDefault(id, Integer.MAX_VALUE)));
        sets.forEach(s -> assertTrue(supplied.getOrDefault(s.foodPost().getId(), 0L) <= s.foodPost().getAvailableQuantity()));
    }
    @Test void reusesUnchangedComponentAndRecomputesChangedOne() {
        var a = set(10, 3, edge(20, .8));
        var b = set(11, 4, edge(21, .9));
        equivalent(List.of(a, b), Map.of(20L, 2, 21L, 3));
        assertEquals(2, incremental.statistics().solvedComponents());
        equivalent(List.of(a, set(11, 2, edge(21, .7))), Map.of(20L, 2, 21L, 3));
        assertEquals(3, incremental.statistics().solvedComponents());
        assertEquals(1, incremental.statistics().reusedComponents());
        equivalent(List.of(a), Map.of(20L, 0));
        assertEquals(4, incremental.statistics().solvedComponents());
    }
    @Test void componentMergeSplitAndDeletionStayEquivalentToBatch() {
        var a = set(10, 2, edge(20, .9));
        var b = set(11, 2, edge(21, .8));
        var caps = Map.of(20L, 2, 21L, 2);
        equivalent(List.of(a, b), caps);
        equivalent(List.of(a, set(11, 2, edge(20, .95), edge(21, .8))), caps);
        equivalent(List.of(a, b), caps);
        equivalent(List.of(b), caps);
        assertTrue(incremental.statistics().reusedComponents() >= 3);
    }
    @Test void reroutesExistingAllocationToAchieveMaximumFlow() {
        equivalent(List.of(set(10, 1, edge(20, .9), edge(21, .8)),
                set(11, 1, edge(20, .7))), Map.of(20L, 1, 21L, 1));
        var result = incremental.allocate(List.of(set(10, 1, edge(20, .9), edge(21, .8)),
                set(11, 1, edge(20, .7))), Map.of(20L, 1, 21L, 1), NOW);
        assertEquals(2, result.maximumFlow());
        assertEquals(.5, result.minimumCost(), 1e-8);
    }
    @Test void randomDynamicUpdatesAgreeWithBatchSolver() {
        Random random = new Random(4719);
        for (int iteration = 0; iteration < 150; iteration++) {
            List<TopKCandidateSet> sets = new ArrayList<>();
            Map<Long, Integer> caps = new HashMap<>();
            for (long receiver = 20; receiver < 24; receiver++) caps.put(receiver, random.nextInt(5));
            for (long post = 10; post < 15; post++) {
                List<MatchingScoreResult> edges = new ArrayList<>();
                for (long receiver = 20; receiver < 24; receiver++)
                    if (random.nextBoolean()) edges.add(edge(receiver, random.nextInt(101) / 100.0));
                sets.add(set(post, random.nextInt(5), edges.toArray(MatchingScoreResult[]::new)));
            }
            equivalent(sets, caps);
        }
    }
    @Test void expiryInvalidatesCachedSupplyAndNegativeCapacityRejected() {
        var a = set(10, 2, edge(20, .8));
        incremental.allocate(List.of(a), Map.of(), NOW);
        assertEquals(0, incremental.allocate(List.of(a), Map.of(), NOW.plusSeconds(7200)).maximumFlow());
        assertThrows(IllegalArgumentException.class, () -> incremental.allocate(List.of(a), Map.of(20L, -1), NOW));
    }
}
