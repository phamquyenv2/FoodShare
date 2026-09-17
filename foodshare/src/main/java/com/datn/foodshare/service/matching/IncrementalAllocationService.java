package com.datn.foodshare.service.matching;

import com.datn.foodshare.service.matching.MinimumCostMaximumFlowService.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Exact incremental allocation at connected-component granularity.
 * A receiver shared by posts couples their flows; disconnected components are independent.
 * Cache keys include every supply, selected edge weight and effective receiver capacity.
 */
@Service
@RequiredArgsConstructor
public class IncrementalAllocationService {
    private final MinimumCostMaximumFlowService solver;
    private static final int MAX_CACHED_COMPONENTS = 256;
    private final Map<ComponentKey, AllocationResult> cache = new LinkedHashMap<>(16, .75f, true);
    private long solvedComponents;
    private long reusedComponents;

    public synchronized AllocationResult allocate(List<TopKCandidateSet> sets, Map<Long, Integer> capacities, Instant now) {
        Objects.requireNonNull(sets); Objects.requireNonNull(capacities); Objects.requireNonNull(now);
        capacities.forEach((id, cap) -> {
            if (id == null || cap == null || cap < 0) throw new IllegalArgumentException("Invalid recipient capacity");
        });
        // Preserve the batch solver's validation and request-time expiration semantics.
        List<TopKCandidateSet> valid = sets.stream()
                .filter(s -> DynamicMatchingGraph.isEligible(s.foodPost(), now)).toList();
        Set<Long> seen = new HashSet<>();
        valid.forEach(s -> {
            if (!seen.add(s.foodPost().getId())) throw new IllegalArgumentException("Duplicate food post");
        });
        Map<Long, List<Integer>> postsByReceiver = new HashMap<>();
        for (int i = 0; i < valid.size(); i++) {
            for (var score : valid.get(i).topCandidates()) {
                if (!Double.isFinite(score.score()) || score.score() < 0 || score.score() > 1)
                    throw new IllegalArgumentException("Invalid matching score");
                postsByReceiver.computeIfAbsent(score.candidate().getId(), ignored -> new ArrayList<>()).add(i);
            }
        }
        boolean[] visited = new boolean[valid.size()];
        List<Allocation> allocations = new ArrayList<>();
        long flow = 0;
        double cost = 0;
        for (int start = 0; start < valid.size(); start++) {
            if (visited[start]) continue;
            List<TopKCandidateSet> component = new ArrayList<>();
            Set<Long> receivers = new TreeSet<>();
            ArrayDeque<Integer> pending = new ArrayDeque<>();
            pending.add(start); visited[start] = true;
            while (!pending.isEmpty()) {
                int index = pending.remove();
                var set = valid.get(index);
                component.add(set);
                for (var score : set.topCandidates()) {
                    long id = score.candidate().getId();
                    if (receivers.add(id)) for (int neighbor : postsByReceiver.get(id)) {
                        if (!visited[neighbor]) { visited[neighbor] = true; pending.add(neighbor); }
                    }
                }
            }
            component.sort(Comparator.comparingLong(s -> s.foodPost().getId()));
            long supply = component.stream().mapToLong(s -> s.foodPost().getAvailableQuantity()).sum();
            Map<Long, Long> effectiveCapacities = new TreeMap<>();
            receivers.forEach(id -> effectiveCapacities.put(id,
                    capacities.containsKey(id) ? capacities.get(id).longValue() : supply));
            List<PostKey> postKeys = component.stream().map(s -> new PostKey(s.foodPost().getId(),
                    s.foodPost().getAvailableQuantity(), s.topCandidates().stream()
                    .map(r -> new EdgeKey(r.candidate().getId(), r.score(), r.candidate().getRole(), r.activeOrderCount()))
                    .sorted(Comparator.comparingLong(EdgeKey::receiverId).thenComparingDouble(EdgeKey::score)).toList())).toList();
            ComponentKey key = new ComponentKey(postKeys, Map.copyOf(effectiveCapacities));
            AllocationResult result = cache.get(key);
            if (result == null) {
                result = solver.allocate(component, capacities, now);
                cache.put(key, result); solvedComponents++;
                if (cache.size() > MAX_CACHED_COMPONENTS) cache.remove(cache.keySet().iterator().next());
            } else reusedComponents++;
            flow += result.maximumFlow(); cost += result.minimumCost(); allocations.addAll(result.allocations());
        }
        allocations.sort(Comparator.comparingLong(Allocation::foodPostId).thenComparingLong(Allocation::candidateId));
        return new AllocationResult(flow, cost, allocations);
    }

    public synchronized Statistics statistics() { return new Statistics(solvedComponents, reusedComponents, cache.size()); }
    public record Statistics(long solvedComponents, long reusedComponents, int cachedComponents) {}
    private record EdgeKey(long receiverId, double score, com.datn.foodshare.util.constant.Role role, long activeOrderCount) {}
    private record PostKey(long postId, int supply, List<EdgeKey> edges) {}
    private record ComponentKey(List<PostKey> posts, Map<Long, Long> capacities) {}
}
