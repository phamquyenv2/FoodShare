package com.datn.foodshare.domain.request;

import com.datn.foodshare.service.matching.MinimumCostMaximumFlowService.TopKCandidateSet;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AdaptiveTopKRequest(
        List<TopKCandidateSet> fullSets,
        Map<Long, Integer> capacities,
        int initialK,
        Instant now
) {}
