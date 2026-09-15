package com.datn.foodshare.domain.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSummaryResponse {

    private Double averageRating;
    private long totalReviews;
}
