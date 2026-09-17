package com.datn.foodshare.domain.response;

import com.datn.foodshare.service.matching.MinimumCostMaximumFlowService.AllocationResult;
import com.datn.foodshare.service.matching.MinimumCostMaximumFlowService.TopKCandidateSet;

import java.util.List;

public record AdaptiveTopKResponse(
        List<TopKCandidateSet> candidateSets,
        AllocationResult allocation,
        Diagnostics diagnostics
) {
    public record Attempt(
            int k,
            long flow,
            double cost
    ) {}

    public record Diagnostics(
            int initialK,
            int effectiveK,
            int maximumK,
            long fullGraphFlow,
            double fullGraphCost,
            boolean certified,
            List<Attempt> attempts
    ) {}
}
