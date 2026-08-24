package com.datn.foodshare.domain.response;

import com.datn.foodshare.domain.entity.Review;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class ReviewResponse {

    private Long id;
    private int rating;
    private String comment;
    private OrderInfo order;
    private ReviewerInfo reviewer;
    private Instant createdAt;

    @Getter
    @Builder
    public static class OrderInfo {
        private Long id;
        private String orderCode;
    }

    @Getter
    @Builder
    public static class ReviewerInfo {
        private Long id;
        private String fullName;
    }

    public static ReviewResponse from(Review review) {
        return ReviewResponse.builder()
                .id(review.getId())
                .rating(review.getRating())
                .comment(review.getComment())
                .order(OrderInfo.builder()
                        .id(review.getOrder().getId())
                        .orderCode(review.getOrder().getOrderCode())
                        .build())
                .reviewer(ReviewerInfo.builder()
                        .id(review.getReviewer().getId())
                        .fullName(review.getReviewer().getFullName())
                        .build())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
