package com.datn.foodshare.controller.admin;

import com.datn.foodshare.domain.response.ReviewResponse;
import com.datn.foodshare.service.ReviewService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminReviewControllerTest {

    @Mock
    private ReviewService reviewService;

    private AdminReviewController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminReviewController(reviewService);
    }

    @Test
    void getAllReviews_returns200() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<ReviewResponse> page = new PageImpl<>(List.of(ReviewResponse.builder().id(1L).build()));
        when(reviewService.adminGetAllReviews(pageable)).thenReturn(page);

        ResponseEntity<Page<ReviewResponse>> result = controller.getAllReviews(pageable);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(page, result.getBody());
    }

    @Test
    void deleteReview_callsServiceAndReturns200() {
        ResponseEntity<Void> result = controller.deleteReview(10L);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(reviewService).adminDeleteReview(10L);
    }
}
