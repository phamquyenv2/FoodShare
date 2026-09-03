package com.datn.foodshare.controller.admin;

import com.datn.foodshare.domain.response.ReviewResponse;
import com.datn.foodshare.service.ReviewService;
import com.datn.foodshare.util.annotation.ApiMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reviews")
@Secured("ROLE_ADMIN")
@RequiredArgsConstructor
public class AdminReviewController {

    private final ReviewService reviewService;

    @GetMapping
    @ApiMessage("Lấy danh sách đánh giá (Admin) thành công")
    public ResponseEntity<Page<ReviewResponse>> getAllReviews(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(reviewService.adminGetAllReviews(pageable));
    }

    @DeleteMapping("/{id}")
    @ApiMessage("Xóa đánh giá vi phạm thành công")
    public ResponseEntity<Void> deleteReview(@PathVariable(name = "id") Long id) {
        reviewService.adminDeleteReview(id);
        return ResponseEntity.ok().build();
    }
}
