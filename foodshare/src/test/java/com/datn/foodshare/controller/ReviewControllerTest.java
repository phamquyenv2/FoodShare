package com.datn.foodshare.controller;

import com.datn.foodshare.domain.request.CreateReviewRequest;
import com.datn.foodshare.domain.response.ReviewResponse;
import com.datn.foodshare.domain.response.ReviewSummaryResponse;
import com.datn.foodshare.service.ReviewService;
import com.datn.foodshare.util.error.PermissionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewControllerTest {

    @Mock
    private ReviewService reviewService;

    private ReviewController controller;

    @BeforeEach
    void setUp() {
        controller = new ReviewController(reviewService);
    }

    private ReviewResponse mockReview(Long id) {
        return ReviewResponse.builder().id(id).rating(5).comment("Great").build();
    }

    @Test
    void createReview_returns201() throws PermissionException {
        CreateReviewRequest request = new CreateReviewRequest();
        ReviewResponse response = mockReview(1L);
        when(reviewService.createReview(request)).thenReturn(response);

        ResponseEntity<ReviewResponse> result = controller.createReview(request);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertSame(response, result.getBody());
    }

    @Test
    void getMyReviews_returns200() throws PermissionException {
        Pageable pageable = PageRequest.of(0, 20);
        Page<ReviewResponse> page = new PageImpl<>(List.of(mockReview(1L)));
        when(reviewService.getMyReviews(pageable)).thenReturn(page);

        ResponseEntity<Page<ReviewResponse>> result = controller.getMyReviews(pageable);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(page, result.getBody());
    }

    @Test
    void getSupplierReviews_returns200() throws PermissionException {
        Pageable pageable = PageRequest.of(0, 20);
        Page<ReviewResponse> page = new PageImpl<>(List.of(mockReview(1L)));
        when(reviewService.getSupplierReviews(pageable)).thenReturn(page);

        ResponseEntity<Page<ReviewResponse>> result = controller.getSupplierReviews(pageable);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(page, result.getBody());
    }

    @Test
    void getBusinessReviewSummary_returns200() {
        ReviewSummaryResponse summary = ReviewSummaryResponse.builder().averageRating(4.5).totalReviews(10L).build();
        when(reviewService.getBusinessReviewSummary(5L)).thenReturn(summary);

        ResponseEntity<ReviewSummaryResponse> result = controller.getBusinessReviewSummary(5L);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(summary, result.getBody());
    }

    @Test
    void getBusinessReviewSummaries_returns200() {
        Map<Long, ReviewSummaryResponse> map = Map.of(5L, ReviewSummaryResponse.builder().averageRating(4.5).build());
        when(reviewService.getBusinessReviewSummaries(List.of(5L))).thenReturn(map);

        ResponseEntity<Map<Long, ReviewSummaryResponse>> result = controller.getBusinessReviewSummaries(List.of(5L));

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(map, result.getBody());
    }

    @Test
    void getBusinessReviews_returns200() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<ReviewResponse> page = new PageImpl<>(List.of(mockReview(1L)));
        when(reviewService.getBusinessReviews(5L, pageable)).thenReturn(page);

        ResponseEntity<Page<ReviewResponse>> result = controller.getBusinessReviews(5L, pageable);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(page, result.getBody());
    }
}
