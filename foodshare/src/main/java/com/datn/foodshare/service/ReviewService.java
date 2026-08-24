package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.Review;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.request.CreateReviewRequest;
import com.datn.foodshare.domain.response.ReviewResponse;
import com.datn.foodshare.repository.OrderRepository;
import com.datn.foodshare.repository.ReviewRepository;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.util.SecurityUtil;
import com.datn.foodshare.util.constant.OrderStatus;
import com.datn.foodshare.util.constant.Role;
import com.datn.foodshare.util.error.BusinessException;
import com.datn.foodshare.util.error.PermissionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    @Transactional
    public ReviewResponse createReview(CreateReviewRequest request) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        requireReceiverRole(currentUser);

        Order order = orderRepository.findByIdWithDetails(request.getOrderId())
                .orElseThrow(() -> new BusinessException("Đơn tiếp nhận không tồn tại: " + request.getOrderId()));

        if (!order.getReceiver().getId().equals(currentUser.getId())) {
            throw new PermissionException("Bạn không có quyền đánh giá đơn tiếp nhận này");
        }

        if (order.getOrderStatus() != OrderStatus.COMPLETED) {
            throw new BusinessException("Chỉ có thể đánh giá đơn tiếp nhận đã hoàn thành");
        }

        if (reviewRepository.existsByOrderId(order.getId())) {
            throw new BusinessException("Đơn tiếp nhận này đã được đánh giá rồi");
        }

        Review review = Review.builder()
                .order(order)
                .reviewer(currentUser)
                .businessProfile(order.getBusinessProfile())
                .rating(request.getRating())
                .comment(trimToNull(request.getComment()))
                .build();

        Review savedReview = reviewRepository.save(review);

        log.info("Đã tạo đánh giá cho đơn {} bởi user {}", order.getOrderCode(), currentUser.getId());

        return ReviewResponse.from(savedReview);
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> getMyReviews(Pageable pageable) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        requireReceiverRole(currentUser);

        return reviewRepository.findByReviewerId(currentUser.getId(), pageable)
                .map(ReviewResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> getSupplierReviews(Pageable pageable) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        if (currentUser.getRole() != Role.SUPPLIER) {
            throw new PermissionException("Chỉ SUPPLIER mới có quyền xem đánh giá nhận được");
        }

        if (currentUser.getBusinessProfile() == null) {
            throw new BusinessException("Không tìm thấy hồ sơ doanh nghiệp của bạn");
        }

        return reviewRepository.findByBusinessProfileId(currentUser.getBusinessProfile().getId(), pageable)
                .map(ReviewResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> adminGetAllReviews(Pageable pageable) {
        return reviewRepository.findAllWithDetails(pageable).map(ReviewResponse::from);
    }

    @Transactional
    public void adminDeleteReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException("Đánh giá không tồn tại"));
        reviewRepository.delete(review);
        log.info("Admin đã xóa review {}", reviewId);
    }

    private User getAuthenticatedUser() {
        Long userId = SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new BadCredentialsException("Không xác định được người dùng hiện tại"));
        return userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Tài khoản không tồn tại"));
    }

    private void requireReceiverRole(User user) throws PermissionException {
        if (user.getRole() != Role.RECIPIENT && user.getRole() != Role.ORGANIZATION) {
            throw new PermissionException("Chỉ RECIPIENT hoặc ORGANIZATION mới có quyền đánh giá");
        }
    }

    private String trimToNull(String value) {
        return (value != null && !value.isBlank()) ? value.trim() : null;
    }
}
