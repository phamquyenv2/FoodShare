package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.Review;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.request.CreateReviewRequest;
import com.datn.foodshare.domain.response.ReviewResponse;
import com.datn.foodshare.domain.response.ReviewSummaryResponse;
import com.datn.foodshare.repository.OrderRepository;
import com.datn.foodshare.repository.ReviewRepository;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.util.SecurityUtil;
import com.datn.foodshare.util.constant.OrderStatus;
import com.datn.foodshare.util.constant.Role;
import com.datn.foodshare.util.error.BusinessException;
import com.datn.foodshare.util.error.PermissionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ReviewService reviewService;

    private static final Long RECIPIENT_USER_ID = 50L;
    private static final Long SUPPLIER_USER_ID = 10L;
    private static final Long ORDER_ID = 200L;
    private static final Long REVIEW_ID = 300L;
    private static final Long BUSINESS_PROFILE_ID = 1L;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(reviewRepository, orderRepository, userRepository, eventPublisher);
    }


    @Test
    void createReview_success() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));

            Order order = completedOrder();
            when(orderRepository.findByIdWithDetails(ORDER_ID)).thenReturn(Optional.of(order));
            when(reviewRepository.existsByOrderId(ORDER_ID)).thenReturn(false);
            when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
                Review review = inv.getArgument(0);
                review.setId(REVIEW_ID);
                return review;
            });

            CreateReviewRequest request = validCreateReviewRequest();
            ReviewResponse response = reviewService.createReview(request);

            assertNotNull(response);
            assertEquals(5, response.getRating());
            assertEquals("Rất tốt!", response.getComment());
            assertEquals(ORDER_ID, response.getOrder().getId());
            assertEquals(RECIPIENT_USER_ID, response.getReviewer().getId());
            verify(reviewRepository).save(any(Review.class));
        }
    }

    @Test
    void createReview_success_withNullComment() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));

            Order order = completedOrder();
            when(orderRepository.findByIdWithDetails(ORDER_ID)).thenReturn(Optional.of(order));
            when(reviewRepository.existsByOrderId(ORDER_ID)).thenReturn(false);
            when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
                Review review = inv.getArgument(0);
                review.setId(REVIEW_ID);
                return review;
            });

            CreateReviewRequest request = validCreateReviewRequest();
            request.setComment(null);
            ReviewResponse response = reviewService.createReview(request);

            assertNotNull(response);
            assertNull(response.getComment());
        }
    }

    // ===========================
    // Kiểm tra quyền
    // ===========================

    @Test
    void createReview_rejectsSupplierRole() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(SUPPLIER_USER_ID));
            User supplier = new User();
            supplier.setId(SUPPLIER_USER_ID);
            supplier.setRole(Role.SUPPLIER);
            when(userRepository.findById(SUPPLIER_USER_ID)).thenReturn(Optional.of(supplier));

            assertThrows(PermissionException.class, () -> reviewService.createReview(validCreateReviewRequest()));
            verify(reviewRepository, never()).save(any());
        }
    }

    @Test
    void createReview_rejectsOtherUserOrder() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));

            Order order = completedOrder();
            User otherUser = new User();
            otherUser.setId(999L);
            order.setReceiver(otherUser);

            when(orderRepository.findByIdWithDetails(ORDER_ID)).thenReturn(Optional.of(order));

            assertThrows(PermissionException.class, () -> reviewService.createReview(validCreateReviewRequest()));
            verify(reviewRepository, never()).save(any());
        }
    }

    // ===========================
    // Kiểm tra trạng thái Order
    // ===========================

    @Test
    void createReview_rejectsPendingOrder() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));

            Order order = completedOrder();
            order.setOrderStatus(OrderStatus.PENDING);
            when(orderRepository.findByIdWithDetails(ORDER_ID)).thenReturn(Optional.of(order));

            assertThrows(BusinessException.class, () -> reviewService.createReview(validCreateReviewRequest()));
            verify(reviewRepository, never()).save(any());
        }
    }

    @Test
    void createReview_rejectsAcceptedOrder() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));

            Order order = completedOrder();
            order.setOrderStatus(OrderStatus.ACCEPTED);
            when(orderRepository.findByIdWithDetails(ORDER_ID)).thenReturn(Optional.of(order));

            assertThrows(BusinessException.class, () -> reviewService.createReview(validCreateReviewRequest()));
            verify(reviewRepository, never()).save(any());
        }
    }

    @Test
    void createReview_rejectsCancelledOrder() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));

            Order order = completedOrder();
            order.setOrderStatus(OrderStatus.CANCELLED);
            when(orderRepository.findByIdWithDetails(ORDER_ID)).thenReturn(Optional.of(order));

            assertThrows(BusinessException.class, () -> reviewService.createReview(validCreateReviewRequest()));
            verify(reviewRepository, never()).save(any());
        }
    }

    // ===========================
    // Kiểm tra duplicate
    // ===========================

    @Test
    void createReview_rejectsDuplicateReview() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));

            Order order = completedOrder();
            when(orderRepository.findByIdWithDetails(ORDER_ID)).thenReturn(Optional.of(order));
            when(reviewRepository.existsByOrderId(ORDER_ID)).thenReturn(true);

            assertThrows(BusinessException.class, () -> reviewService.createReview(validCreateReviewRequest()));
            verify(reviewRepository, never()).save(any());
        }
    }

    // ===========================
    // Lấy danh sách đánh giá
    // ===========================

    @Test
    void getMyReviews_success() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));

            Review review = new Review();
            review.setId(REVIEW_ID);
            review.setRating(5);
            review.setComment("Tốt");
            review.setReviewer(recipientUser());
            review.setOrder(completedOrder());

            Pageable pageable = PageRequest.of(0, 10);
            Page<Review> page = new PageImpl<>(List.of(review), pageable, 1);
            when(reviewRepository.findByReviewerId(RECIPIENT_USER_ID, pageable)).thenReturn(page);

            Page<ReviewResponse> result = reviewService.getMyReviews(pageable);
            assertEquals(1, result.getTotalElements());
            assertEquals(REVIEW_ID, result.getContent().get(0).getId());
        }
    }

    @Test
    void getSupplierReviews_success() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(SUPPLIER_USER_ID));
            User supplier = new User();
            supplier.setId(SUPPLIER_USER_ID);
            supplier.setRole(Role.SUPPLIER);
            BusinessProfile bp = businessProfile();
            supplier.setBusinessProfile(bp);
            when(userRepository.findById(SUPPLIER_USER_ID)).thenReturn(Optional.of(supplier));

            Review review = new Review();
            review.setId(REVIEW_ID);
            review.setRating(4);
            review.setReviewer(recipientUser());
            review.setOrder(completedOrder());

            Pageable pageable = PageRequest.of(0, 10);
            Page<Review> page = new PageImpl<>(List.of(review), pageable, 1);
            when(reviewRepository.findByBusinessProfileId(BUSINESS_PROFILE_ID, pageable)).thenReturn(page);

            Page<ReviewResponse> result = reviewService.getSupplierReviews(pageable);
            assertEquals(1, result.getTotalElements());
        }
    }

    @Test
    void getSupplierReviews_rejectsRecipientRole() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));

            assertThrows(PermissionException.class,
                    () -> reviewService.getSupplierReviews(PageRequest.of(0, 10)));
        }
    }

    // ===========================
    // Admin Review Management
    // ===========================

    @Test
    void adminGetAllReviews_success() {
        Review review = new Review();
        review.setId(REVIEW_ID);
        review.setRating(5);
        review.setComment("Tuyệt vời");
        review.setReviewer(recipientUser());
        review.setOrder(completedOrder());

        Pageable pageable = PageRequest.of(0, 10);
        Page<Review> page = new PageImpl<>(List.of(review), pageable, 1);
        when(reviewRepository.findAllWithDetails(pageable)).thenReturn(page);

        Page<ReviewResponse> result = reviewService.adminGetAllReviews(pageable);
        assertEquals(1, result.getTotalElements());
        assertEquals(REVIEW_ID, result.getContent().get(0).getId());
    }

    @Test
    void adminDeleteReview_success() {
        Review review = new Review();
        review.setId(REVIEW_ID);
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));

        reviewService.adminDeleteReview(REVIEW_ID);

        verify(reviewRepository).delete(review);
    }

    @Test
    void adminDeleteReview_rejectsNotFound() {
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> reviewService.adminDeleteReview(REVIEW_ID));
        verify(reviewRepository, never()).delete(any());
    }

    @Test
    void getBusinessReviewSummary_success() {
        when(reviewRepository.getAverageRatingByBusinessProfileId(BUSINESS_PROFILE_ID)).thenReturn(4.78);
        when(reviewRepository.countByBusinessProfileId(BUSINESS_PROFILE_ID)).thenReturn(15L);

        ReviewSummaryResponse summary = reviewService.getBusinessReviewSummary(BUSINESS_PROFILE_ID);

        assertNotNull(summary);
        assertEquals(4.8, summary.getAverageRating());
        assertEquals(15L, summary.getTotalReviews());
    }

    @Test
    void getBusinessReviewSummary_noReviews() {
        when(reviewRepository.getAverageRatingByBusinessProfileId(BUSINESS_PROFILE_ID)).thenReturn(null);
        when(reviewRepository.countByBusinessProfileId(BUSINESS_PROFILE_ID)).thenReturn(0L);

        ReviewSummaryResponse summary = reviewService.getBusinessReviewSummary(BUSINESS_PROFILE_ID);

        assertNotNull(summary);
        assertNull(summary.getAverageRating());
        assertEquals(0L, summary.getTotalReviews());
    }

    @Test
    void getBusinessReviewSummaries_success() {
        Object[] row1 = new Object[]{1L, 4.5, 10L};
        Object[] row2 = new Object[]{2L, 5.0, 3L};
        when(reviewRepository.getReviewSummariesByBusinessProfileIds(List.of(1L, 2L)))
                .thenReturn(List.of(row1, row2));

        var map = reviewService.getBusinessReviewSummaries(List.of(1L, 2L));

        assertEquals(2, map.size());
        assertEquals(4.5, map.get(1L).getAverageRating());
        assertEquals(10L, map.get(1L).getTotalReviews());
        assertEquals(5.0, map.get(2L).getAverageRating());
        assertEquals(3L, map.get(2L).getTotalReviews());
    }

    @Test
    void getBusinessReviews_success() {
        Review review = Review.builder()
                .id(REVIEW_ID)
                .order(completedOrder())
                .reviewer(recipientUser())
                .businessProfile(businessProfile())
                .rating(5)
                .comment("Tuyệt vời")
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        when(reviewRepository.findByBusinessProfileId(BUSINESS_PROFILE_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(review), pageable, 1));

        Page<ReviewResponse> result = reviewService.getBusinessReviews(BUSINESS_PROFILE_ID, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(5, result.getContent().get(0).getRating());
        assertEquals("Tuyệt vời", result.getContent().get(0).getComment());
    }

    // ===========================
    // Helper Methods
    // ===========================

    private User recipientUser() {
        User user = new User();
        user.setId(RECIPIENT_USER_ID);
        user.setRole(Role.RECIPIENT);
        user.setProfileCompleted(true);
        user.setFullName("Recipient A");
        user.setActive(true);
        return user;
    }

    private BusinessProfile businessProfile() {
        User supplier = new User();
        supplier.setId(SUPPLIER_USER_ID);
        supplier.setRole(Role.SUPPLIER);
        supplier.setFullName("Supplier A");

        BusinessProfile bp = new BusinessProfile();
        bp.setId(BUSINESS_PROFILE_ID);
        bp.setUser(supplier);
        bp.setName("Cửa hàng A");
        return bp;
    }

    private Order completedOrder() {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setOrderCode("ORD-TEST1234");
        order.setOrderStatus(OrderStatus.COMPLETED);
        order.setReceiver(recipientUser());
        order.setBusinessProfile(businessProfile());
        order.setTotalAmount(BigDecimal.ZERO);
        return order;
    }

    private CreateReviewRequest validCreateReviewRequest() {
        CreateReviewRequest request = new CreateReviewRequest();
        request.setOrderId(ORDER_ID);
        request.setRating(5);
        request.setComment("Rất tốt!");
        return request;
    }
}
