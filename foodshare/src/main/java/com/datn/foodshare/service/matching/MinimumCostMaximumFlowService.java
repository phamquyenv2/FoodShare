package com.datn.foodshare.service.matching;

import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.service.matching.MatchingScoreCalculator.MatchingScoreResult;
import com.datn.foodshare.util.constant.PostStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class MinimumCostMaximumFlowService {

    public AllocationResult allocate(List<TopKCandidateSet> candidateSets) {
        return allocate(candidateSets, Map.of());
    }

    public AllocationResult allocate(
            List<TopKCandidateSet> candidateSets,
            Map<Long, Integer> recipientCapacities
    ) {
        return allocate(candidateSets, recipientCapacities, Instant.now());
    }

    AllocationResult allocate(
            List<TopKCandidateSet> candidateSets,
            Map<Long, Integer> recipientCapacities,
            Instant evaluatedAt
    ) {
        Objects.requireNonNull(candidateSets, "Top-K candidate sets must not be null");
        Objects.requireNonNull(recipientCapacities, "Recipient capacities must not be null");
        Objects.requireNonNull(evaluatedAt, "Evaluation time must not be null");
        validateRecipientCapacities(recipientCapacities);

        List<PreparedCandidateSet> preparedSets = prepare(candidateSets, evaluatedAt);
        if (preparedSets.isEmpty()) {
            return AllocationResult.empty();
        }

        long totalSupply = preparedSets.stream()
                .mapToLong(PreparedCandidateSet::availableQuantity)
                .sum();
        if (totalSupply == 0) {
            return AllocationResult.empty();
        }

        Set<Long> candidateIds = new HashSet<>();
        preparedSets.forEach(set -> set.candidates().forEach(
                candidate -> candidateIds.add(candidate.candidateId())
        ));
        if (candidateIds.isEmpty()) {
            return AllocationResult.empty();
        }

        List<Long> orderedCandidateIds = candidateIds.stream().sorted().toList();
        int source = 0;
        int firstFoodPostNode = 1;
        int firstCandidateNode = firstFoodPostNode + preparedSets.size();
        int sink = firstCandidateNode + orderedCandidateIds.size();
        FlowNetwork network = new FlowNetwork(sink + 1);

        Map<Long, Integer> candidateNodes = new HashMap<>();
        for (int index = 0; index < orderedCandidateIds.size(); index++) {
            long candidateId = orderedCandidateIds.get(index);
            int candidateNode = firstCandidateNode + index;
            candidateNodes.put(candidateId, candidateNode);
            Integer configuredCapacity = recipientCapacities.get(candidateId);
            long capacity = configuredCapacity == null ? totalSupply : configuredCapacity;
            network.addEdge(candidateNode, sink, capacity, 0.0);
        }

        List<AllocationArc> allocationArcs = new ArrayList<>();
        for (int index = 0; index < preparedSets.size(); index++) {
            PreparedCandidateSet candidateSet = preparedSets.get(index);
            int foodPostNode = firstFoodPostNode + index;
            network.addEdge(source, foodPostNode, candidateSet.availableQuantity(), 0.0);

            for (PreparedCandidate candidate : candidateSet.candidates()) {
                long recipientCapacity = recipientCapacities.getOrDefault(
                        candidate.candidateId(),
                        candidateSet.availableQuantity()
                );
                long capacity = Math.min(candidateSet.availableQuantity(), recipientCapacity);
                FlowEdge edge = network.addEdge(
                        foodPostNode,
                        candidateNodes.get(candidate.candidateId()),
                        capacity,
                        scoreToCost(candidate.score())
                );
                allocationArcs.add(new AllocationArc(
                        candidateSet.foodPostId(),
                        candidate.candidateId(),
                        candidate.score(),
                        edge
                ));
            }
        }

        FlowResult flowResult = network.solve(source, sink);
        List<Allocation> allocations = allocationArcs.stream()
                .map(AllocationArc::toAllocation)
                .filter(allocation -> allocation.quantity() > 0)
                .sorted(Comparator.comparingLong(Allocation::foodPostId)
                        .thenComparingLong(Allocation::candidateId))
                .toList();

        return new AllocationResult(flowResult.flow(), flowResult.cost(), allocations);
    }

    private List<PreparedCandidateSet> prepare(
            List<TopKCandidateSet> candidateSets,
            Instant evaluatedAt
    ) {
        Set<Long> foodPostIds = new HashSet<>();
        List<PreparedCandidateSet> preparedSets = new ArrayList<>();

        for (TopKCandidateSet candidateSet : candidateSets) {
            Objects.requireNonNull(candidateSet, "Top-K candidate set must not be null");
            FoodPost foodPost = Objects.requireNonNull(
                    candidateSet.foodPost(),
                    "FoodPost must not be null"
            );
            if (foodPost.getId() == null) {
                throw new IllegalArgumentException("FoodPost ID must not be null");
            }
            if (!foodPostIds.add(foodPost.getId())) {
                throw new IllegalArgumentException("Duplicate FoodPost ID: " + foodPost.getId());
            }
            if (!isEligible(foodPost, evaluatedAt)) {
                continue;
            }

            Map<Long, PreparedCandidate> bestCandidateById = new LinkedHashMap<>();
            for (MatchingScoreResult result : candidateSet.topCandidates()) {
                Objects.requireNonNull(result, "Top-K result must not be null");
                if (result.candidate() == null || result.candidate().getId() == null) {
                    throw new IllegalArgumentException("Top-K candidate ID must not be null");
                }
                validateScore(result.score());
                PreparedCandidate prepared = new PreparedCandidate(
                        result.candidate().getId(),
                        result.score()
                );
                bestCandidateById.merge(
                        prepared.candidateId(),
                        prepared,
                        (current, replacement) -> replacement.score() > current.score()
                                ? replacement
                                : current
                );
            }

            List<PreparedCandidate> candidates = bestCandidateById.values().stream()
                    .sorted(Comparator.comparingLong(PreparedCandidate::candidateId))
                    .toList();
            preparedSets.add(new PreparedCandidateSet(
                    foodPost.getId(),
                    foodPost.getAvailableQuantity(),
                    candidates
            ));
        }

        preparedSets.sort(Comparator.comparingLong(PreparedCandidateSet::foodPostId));
        return preparedSets;
    }

    private void validateRecipientCapacities(Map<Long, Integer> recipientCapacities) {
        recipientCapacities.forEach((candidateId, capacity) -> {
            if (candidateId == null) {
                throw new IllegalArgumentException("Recipient capacity ID must not be null");
            }
            if (capacity == null || capacity < 0) {
                throw new IllegalArgumentException(
                        "Recipient capacity must be non-negative for candidate " + candidateId
                );
            }
        });
    }

    private boolean isEligible(FoodPost foodPost, Instant evaluatedAt) {
        return foodPost.getPostStatus() == PostStatus.AVAILABLE
                && foodPost.getAvailableQuantity() > 0
                && foodPost.getExpiresAt() != null
                && foodPost.getExpiresAt().isAfter(evaluatedAt);
    }

    private void validateScore(double score) {
        if (!Double.isFinite(score) || score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("Matching score must be between 0 and 1");
        }
    }

    private double scoreToCost(double score) {
        return 1.0 - score;
    }

    public record TopKCandidateSet(FoodPost foodPost, List<MatchingScoreResult> topCandidates) {
        public TopKCandidateSet {
            Objects.requireNonNull(foodPost, "FoodPost must not be null");
            topCandidates = List.copyOf(Objects.requireNonNull(
                    topCandidates,
                    "Top-K candidates must not be null"
            ));
        }
    }

    public record Allocation(
            long foodPostId,
            long candidateId,
            long quantity,
            double unitCost,
            double score
    ) {
    }

    public record AllocationResult(long maximumFlow, double minimumCost, List<Allocation> allocations) {
        public AllocationResult {
            allocations = List.copyOf(allocations);
        }

        static AllocationResult empty() {
            return new AllocationResult(0, 0.0, List.of());
        }
    }

    private record PreparedCandidateSet(
            long foodPostId,
            int availableQuantity,
            List<PreparedCandidate> candidates
    ) {
    }

    private record PreparedCandidate(long candidateId, double score) {
    }

    private record AllocationArc(long foodPostId, long candidateId, double score, FlowEdge edge) {
        Allocation toAllocation() {
            return new Allocation(
                    foodPostId,
                    candidateId,
                    edge.originalCapacity - edge.capacity,
                    edge.cost,
                    score
            );
        }
    }

    private record FlowResult(long flow, double cost) {
    }

    private static final class FlowNetwork {
        private final List<List<FlowEdge>> adjacency;

        private FlowNetwork(int nodeCount) {
            adjacency = new ArrayList<>(nodeCount);
            for (int node = 0; node < nodeCount; node++) {
                adjacency.add(new ArrayList<>());
            }
        }

        private FlowEdge addEdge(int from, int to, long capacity, double cost) {
            FlowEdge forward = new FlowEdge(to, adjacency.get(to).size(), capacity, cost);
            FlowEdge reverse = new FlowEdge(from, adjacency.get(from).size(), 0, -cost);
            adjacency.get(from).add(forward);
            adjacency.get(to).add(reverse);
            return forward;
        }

        private FlowResult solve(int source, int sink) {
            long totalFlow = 0;
            double totalCost = 0.0;
            int nodeCount = adjacency.size();

            while (true) {
                double[] distance = new double[nodeCount];
                Arrays.fill(distance, Double.POSITIVE_INFINITY);
                distance[source] = 0.0;
                int[] previousNode = new int[nodeCount];
                int[] previousEdge = new int[nodeCount];
                Arrays.fill(previousNode, -1);

                for (int iteration = 0; iteration < nodeCount - 1; iteration++) {
                    boolean changed = false;
                    for (int from = 0; from < nodeCount; from++) {
                        if (!Double.isFinite(distance[from])) {
                            continue;
                        }
                        List<FlowEdge> edges = adjacency.get(from);
                        for (int edgeIndex = 0; edgeIndex < edges.size(); edgeIndex++) {
                            FlowEdge edge = edges.get(edgeIndex);
                            if (edge.capacity == 0) {
                                continue;
                            }
                            double candidateDistance = distance[from] + edge.cost;
                            if (candidateDistance < distance[edge.to]) {
                                distance[edge.to] = candidateDistance;
                                previousNode[edge.to] = from;
                                previousEdge[edge.to] = edgeIndex;
                                changed = true;
                            }
                        }
                    }
                    if (!changed) {
                        break;
                    }
                }

                if (previousNode[sink] == -1) {
                    return new FlowResult(totalFlow, totalCost);
                }

                long augmentation = Long.MAX_VALUE;
                for (int node = sink; node != source; node = previousNode[node]) {
                    FlowEdge edge = adjacency.get(previousNode[node]).get(previousEdge[node]);
                    augmentation = Math.min(augmentation, edge.capacity);
                }
                for (int node = sink; node != source; node = previousNode[node]) {
                    int from = previousNode[node];
                    FlowEdge edge = adjacency.get(from).get(previousEdge[node]);
                    edge.capacity -= augmentation;
                    adjacency.get(edge.to).get(edge.reverseIndex).capacity += augmentation;
                }

                totalFlow += augmentation;
                totalCost += augmentation * distance[sink];
            }
        }
    }

    private static final class FlowEdge {
        private final int to;
        private final int reverseIndex;
        private final long originalCapacity;
        private final double cost;
        private long capacity;

        private FlowEdge(int to, int reverseIndex, long capacity, double cost) {
            this.to = to;
            this.reverseIndex = reverseIndex;
            this.capacity = capacity;
            this.originalCapacity = capacity;
            this.cost = cost;
        }
    }
}
