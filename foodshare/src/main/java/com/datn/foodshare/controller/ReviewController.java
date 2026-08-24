package com.datn.foodshare.controller;

import com.datn.foodshare.domain.request.CreateReviewRequest;
import com.datn.foodshare.domain.response.ReviewResponse;
import com.datn.foodshare.service.ReviewService;
import com.datn.foodshare.util.annotation.ApiMessage;
import com.datn.foodshare.util.error.PermissionException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    @Secured({"ROLE_RECIPIENT", "ROLE_ORGANIZATION"})
    @ApiMessage("Tạo đánh giá thành công")
    public ResponseEntity<ReviewResponse> createReview(@Valid @RequestBody CreateReviewRequest request) throws PermissionException {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.createReview(request));
    }

    @GetMapping
    @Secured({"ROLE_RECIPIENT", "ROLE_ORGANIZATION"})
    @ApiMessage("Lấy danh sách đánh giá của tôi thành công")
    public ResponseEntity<Page<ReviewResponse>> getMyReviews(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable)
            throws PermissionException {
        return ResponseEntity.ok(reviewService.getMyReviews(pageable));
    }

    @GetMapping("/supplier")
    @Secured("ROLE_SUPPLIER")
    @ApiMessage("Lấy danh sách đánh giá nhận được thành công")
    public ResponseEntity<Page<ReviewResponse>> getSupplierReviews(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable)
            throws PermissionException {
        return ResponseEntity.ok(reviewService.getSupplierReviews(pageable));
    }
}
