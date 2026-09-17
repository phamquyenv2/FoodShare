package com.datn.foodshare.service.matching;

import com.datn.foodshare.domain.request.AdaptiveTopKRequest;
import com.datn.foodshare.domain.response.AdaptiveTopKResponse;
import com.datn.foodshare.domain.response.AdaptiveTopKResponse.Attempt;
import com.datn.foodshare.domain.response.AdaptiveTopKResponse.Diagnostics;
import com.datn.foodshare.service.matching.MinimumCostMaximumFlowService.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AdaptiveTopKAllocator {

    private final IncrementalAllocationService allocator;

    public AdaptiveTopKAllocator(IncrementalAllocationService allocator) {
        this.allocator = allocator;
    }

    public AdaptiveTopKResponse allocate(AdaptiveTopKRequest request) {
        Objects.requireNonNull(request, "Yêu cầu phân bổ (request) không được để trống (null)");
        return allocate(request.fullSets(), request.capacities(), request.initialK(), request.now());
    }

    public AdaptiveTopKResponse allocate(
            List<TopKCandidateSet> fullSets,
            Map<Long, Integer> capacities,
            int initialK,
            Instant now
    ) {
        if (initialK <= 0) {
            throw new IllegalArgumentException("Giá trị Top-K phải lớn hơn 0");
        }
        Objects.requireNonNull(fullSets, "Danh sách tập ứng viên không được để trống (null)");
        Objects.requireNonNull(capacities, "Danh sách sức chứa người nhận không được để trống (null)");
        Objects.requireNonNull(now, "Thời gian đánh giá không được để trống (null)");

        List<TopKCandidateSet> ordered = fullSets.stream()
                .map(set -> new TopKCandidateSet(
                        set.foodPost(),
                        set.topCandidates().stream().sorted().toList()
                ))
                .toList();

        int maximumK = ordered.stream()
                .mapToInt(s -> s.topCandidates().size())
                .max()
                .orElse(0);

        AllocationResult reference = allocator.allocate(ordered, capacities, now);
        int k = Math.min(initialK, maximumK);
        List<Attempt> attempts = new ArrayList<>();

        while (k < maximumK) {
            final int retained = k;
            var selected = ordered.stream()
                    .map(s -> new TopKCandidateSet(
                            s.foodPost(),
                            s.topCandidates().stream().limit(retained).toList()
                    ))
                    .toList();

            AllocationResult allocation = allocator.allocate(selected, capacities, now);
            attempts.add(new Attempt(k, allocation.maximumFlow(), allocation.minimumCost()));

            double tolerance = 1e-8 * Math.max(1, reference.maximumFlow());
            if (allocation.maximumFlow() == reference.maximumFlow()
                    && Math.abs(allocation.minimumCost() - reference.minimumCost()) <= tolerance) {
                return new AdaptiveTopKResponse(
                        selected,
                        allocation,
                        new Diagnostics(
                                initialK,
                                k,
                                maximumK,
                                reference.maximumFlow(),
                                reference.minimumCost(),
                                true,
                                List.copyOf(attempts)
                        )
                );
            }

            k = (k > maximumK / 2) ? maximumK : Math.min(maximumK, k * 2);
        }

        attempts.add(new Attempt(maximumK, reference.maximumFlow(), reference.minimumCost()));
        return new AdaptiveTopKResponse(
                ordered,
                reference,
                new Diagnostics(
                        initialK,
                        maximumK,
                        maximumK,
                        reference.maximumFlow(),
                        reference.minimumCost(),
                        true,
                        List.copyOf(attempts)
                )
        );
    }
}

