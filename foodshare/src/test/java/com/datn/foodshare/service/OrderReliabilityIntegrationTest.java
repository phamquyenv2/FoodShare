package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.Category;
import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.request.CreateOrderRequest;
import com.datn.foodshare.repository.FoodPostRepository;
import com.datn.foodshare.repository.OrderRepository;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.util.SecurityUtil;
import com.datn.foodshare.util.constant.OrderStatus;
import com.datn.foodshare.util.constant.PostStatus;
import com.datn.foodshare.util.constant.PostType;
import com.datn.foodshare.util.constant.Role;
import com.datn.foodshare.util.error.BusinessException;
import com.datn.foodshare.util.error.PermissionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử tính tin cậy (Reliability) của OrderService:
 * - Transaction rollback khi thất bại
 * - Không dữ liệu cập nhật một phần
 * - Concurrent order behavior (Optimistic Locking)
 */
@ExtendWith(MockitoExtension.class)
class OrderReliabilityIntegrationTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private FoodPostRepository foodPostRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FoodPostService foodPostService;
    @Mock
    private com.datn.foodshare.repository.PaymentRepository paymentRepository;
    @Mock
    private com.datn.foodshare.service.payment.strategy.PaymentStrategyFactory paymentStrategyFactory;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private OrderService orderService;

    private static final Long RECIPIENT_USER_ID = 50L;
    private static final Long SUPPLIER_USER_ID = 10L;
    private static final Long FOOD_POST_ID = 100L;
    private static final Long BUSINESS_PROFILE_ID = 1L;
    private static final Long ORDER_ID = 200L;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository,
                foodPostRepository,
                userRepository,
                foodPostService,
                paymentRepository,
                paymentStrategyFactory,
                eventPublisher
        );
    }

    // ==============================
    // Transaction Rollback Tests
    // ==============================

    @Nested
    @DisplayName("Transaction Rollback")
    class TransactionRollbackTests {

        @Test
        @DisplayName("Rollback khi quantity vượt quá availableQuantity - order không được lưu")
        void rollback_whenQuantityExceedsAvailable_orderNotSaved() {
            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
                when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));

                FoodPost post = availableFoodPost();
                post.setAvailableQuantity(5);
                when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(post));

                CreateOrderRequest request = validCreateOrderRequest();
                request.setQuantity(20); // Vượt quá available=5

                BusinessException ex = assertThrows(BusinessException.class,
                        () -> orderService.createOrder(request));

                assertTrue(ex.getMessage().contains("Không đủ số lượng"));
                // Order KHÔNG được save vì validation fail trước khi save
                verify(orderRepository, never()).save(any(Order.class));
                // Quantity KHÔNG bị giảm
                verify(foodPostService, never()).decreaseQuantity(anyLong(), anyInt());
            }
        }

        @Test
        @DisplayName("Rollback khi FoodPost không ở trạng thái AVAILABLE - order không được lưu")
        void rollback_whenFoodPostNotAvailable_orderNotSaved() {
            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
                when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));

                FoodPost post = availableFoodPost();
                post.setPostStatus(PostStatus.HIDDEN);
                when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(post));

                CreateOrderRequest request = validCreateOrderRequest();

                assertThrows(BusinessException.class,
                        () -> orderService.createOrder(request));

                verify(orderRepository, never()).save(any(Order.class));
                verify(foodPostService, never()).decreaseQuantity(anyLong(), anyInt());
            }
        }

        @Test
        @DisplayName("Rollback khi FoodPost đã hết hạn - order không được lưu")
        void rollback_whenFoodPostExpired_orderNotSaved() {
            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
                when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));

                FoodPost post = availableFoodPost();
                post.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS)); // Đã hết hạn
                when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(post));

                CreateOrderRequest request = validCreateOrderRequest();

                assertThrows(BusinessException.class,
                        () -> orderService.createOrder(request));

                verify(orderRepository, never()).save(any(Order.class));
                verify(foodPostService, never()).decreaseQuantity(anyLong(), anyInt());
            }
        }

        @Test
        @DisplayName("Rollback khi decreaseQuantity thất bại - transaction phải rollback toàn bộ")
        void rollback_whenDecreaseQuantityFails_transactionRollsBack() {
            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
                when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
                when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(availableFoodPost()));
                when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
                    Order order = inv.getArgument(0);
                    order.setId(ORDER_ID);
                    return order;
                });

                // Simulate decreaseQuantity throwing exception AFTER order.save()
                doThrow(new BusinessException("Bài đăng không ở trạng thái khả dụng"))
                        .when(foodPostService).decreaseQuantity(FOOD_POST_ID, 3);

                CreateOrderRequest request = validCreateOrderRequest();

                // @Transactional trên createOrder sẽ rollback cả order.save()
                // khi decreaseQuantity ném exception
                assertThrows(BusinessException.class,
                        () -> orderService.createOrder(request));

                // Verify order.save() was called (but will be rolled back by @Transactional)
                verify(orderRepository).save(any(Order.class));
                // Verify decreaseQuantity was called and threw
                verify(foodPostService).decreaseQuantity(FOOD_POST_ID, 3);
            }
        }

        @Test
        @DisplayName("Rollback khi FoodPost không tồn tại - order không được lưu")
        void rollback_whenFoodPostNotFound_orderNotSaved() {
            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
                when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
                when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.empty());

                CreateOrderRequest request = validCreateOrderRequest();

                assertThrows(BusinessException.class,
                        () -> orderService.createOrder(request));

                verify(orderRepository, never()).save(any(Order.class));
                verify(foodPostService, never()).decreaseQuantity(anyLong(), anyInt());
            }
        }
    }

    // ==============================
    // Partial Update Prevention
    // ==============================

    @Nested
    @DisplayName("No Partial Data Update")
    class NoPartialUpdateTests {

        @Test
        @DisplayName("Order + OrderDetail được lưu cùng nhau trong 1 transaction (cascade)")
        void orderAndOrderDetail_savedTogether() throws PermissionException {
            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
                when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
                when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(availableFoodPost()));
                when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
                    Order order = inv.getArgument(0);
                    order.setId(ORDER_ID);
                    return order;
                });

                CreateOrderRequest request = validCreateOrderRequest();
                orderService.createOrder(request);

                // Verify order + orderDetails are saved in a single save call (cascade)
                ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
                verify(orderRepository, times(1)).save(orderCaptor.capture());

                Order savedOrder = orderCaptor.getValue();
                assertNotNull(savedOrder.getOrderDetails());
                assertEquals(1, savedOrder.getOrderDetails().size());
                assertEquals(FOOD_POST_ID, savedOrder.getOrderDetails().get(0).getFoodPost().getId());
                assertEquals(3, savedOrder.getOrderDetails().get(0).getQuantity());

                // Order trạng thái PENDING
                assertEquals(OrderStatus.PENDING, savedOrder.getOrderStatus());
                assertNotNull(savedOrder.getOrderCode());
            }
        }

        @Test
        @DisplayName("decreaseQuantity được gọi SAU khi order được save thành công")
        void decreaseQuantity_calledAfterOrderSave() throws PermissionException {
            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
                when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
                when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(availableFoodPost()));
                when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
                    Order order = inv.getArgument(0);
                    order.setId(ORDER_ID);
                    return order;
                });

                CreateOrderRequest request = validCreateOrderRequest();
                orderService.createOrder(request);

                // InOrder verifies the call sequence: save → decreaseQuantity
                var inOrder = inOrder(orderRepository, foodPostService);
                inOrder.verify(orderRepository).save(any(Order.class));
                inOrder.verify(foodPostService).decreaseQuantity(FOOD_POST_ID, 3);
            }
        }

        @Test
        @DisplayName("Notification event chỉ được publish sau khi order + quantity thành công")
        void notificationEvent_publishedAfterSuccess() throws PermissionException {
            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
                when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
                when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(availableFoodPost()));
                when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
                    Order order = inv.getArgument(0);
                    order.setId(ORDER_ID);
                    return order;
                });

                CreateOrderRequest request = validCreateOrderRequest();
                orderService.createOrder(request);

                // Notification event published last
                var inOrder = inOrder(orderRepository, foodPostService, eventPublisher);
                inOrder.verify(orderRepository).save(any(Order.class));
                inOrder.verify(foodPostService).decreaseQuantity(FOOD_POST_ID, 3);
                inOrder.verify(eventPublisher).publishEvent(any());
            }
        }

        @Test
        @DisplayName("Notification event KHÔNG được publish khi order creation thất bại")
        void notificationEvent_notPublishedOnFailure() {
            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
                when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));

                FoodPost post = availableFoodPost();
                post.setAvailableQuantity(0);
                post.setPostStatus(PostStatus.OUT_OF_STOCK);
                when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(post));

                CreateOrderRequest request = validCreateOrderRequest();

                assertThrows(BusinessException.class,
                        () -> orderService.createOrder(request));

                verify(eventPublisher, never()).publishEvent(any());
            }
        }
    }

    // ==============================
    // Concurrent Order Simulation
    // ==============================

    @Nested
    @DisplayName("Concurrent Order")
    class ConcurrentOrderTests {

        @Test
        @DisplayName("Khi decreaseQuantity throw do OptimisticLock - order bị reject")
        void concurrentOrder_optimisticLockFailure_orderRejected() {
            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
                when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
                when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(availableFoodPost()));
                when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
                    Order order = inv.getArgument(0);
                    order.setId(ORDER_ID);
                    return order;
                });

                // Simulate OptimisticLockException from decreaseQuantity
                doThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(
                        FoodPost.class.getName(), FOOD_POST_ID))
                        .when(foodPostService).decreaseQuantity(FOOD_POST_ID, 3);

                CreateOrderRequest request = validCreateOrderRequest();

                // OptimisticLockException propagates → transaction rollback
                assertThrows(org.springframework.orm.ObjectOptimisticLockingFailureException.class,
                        () -> orderService.createOrder(request));
            }
        }

        @Test
        @DisplayName("Khi availableQuantity giảm bởi thread khác → Không đủ số lượng")
        void concurrentOrder_quantityReducedByOtherThread_rejected() {
            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
                when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
                when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(availableFoodPost()));
                when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
                    Order order = inv.getArgument(0);
                    order.setId(ORDER_ID);
                    return order;
                });

                // Simulate another thread already decreased quantity
                doThrow(new BusinessException("Không đủ số lượng. Còn lại: 0"))
                        .when(foodPostService).decreaseQuantity(FOOD_POST_ID, 3);

                CreateOrderRequest request = validCreateOrderRequest();

                BusinessException ex = assertThrows(BusinessException.class,
                        () -> orderService.createOrder(request));

                assertTrue(ex.getMessage().contains("Không đủ số lượng"));
            }
        }
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
        supplier.setProfileCompleted(true);
        supplier.setFullName("Supplier A");

        BusinessProfile bp = new BusinessProfile();
        bp.setId(BUSINESS_PROFILE_ID);
        bp.setUser(supplier);
        bp.setName("Cửa hàng A");
        return bp;
    }

    private Category category() {
        Category cat = new Category();
        cat.setId(1L);
        cat.setName("Bánh và đồ ăn nhẹ");
        return cat;
    }

    private FoodPost availableFoodPost() {
        FoodPost post = FoodPost.builder()
                .name("Bánh mì")
                .description("Còn mới")
                .totalQuantity(10)
                .availableQuantity(10)
                .unitPrice(BigDecimal.ZERO)
                .postType(PostType.FREE)
                .postStatus(PostStatus.AVAILABLE)
                .expiresAt(Instant.now().plus(2, ChronoUnit.DAYS))
                .pickupAddress("123 ABC")
                .pickupStartAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .pickupEndAt(Instant.now().plus(3, ChronoUnit.HOURS))
                .build();
        post.setId(FOOD_POST_ID);
        post.setCategory(category());
        post.setBusinessProfile(businessProfile());
        return post;
    }

    private CreateOrderRequest validCreateOrderRequest() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setFoodPostId(FOOD_POST_ID);
        request.setQuantity(3);
        return request;
    }
}
