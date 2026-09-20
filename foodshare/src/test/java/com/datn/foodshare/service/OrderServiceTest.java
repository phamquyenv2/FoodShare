package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.Category;
import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.OrderDetail;
import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.Payment;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.request.CreateOrderRequest;
import com.datn.foodshare.domain.request.BatchCreateOrderRequest;
import com.datn.foodshare.domain.response.OrderResponse;
import com.datn.foodshare.event.NotificationEvent;
import com.datn.foodshare.repository.FoodPostRepository;
import com.datn.foodshare.repository.OrderRepository;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.util.SecurityUtil;
import com.datn.foodshare.util.constant.OrderStatus;
import com.datn.foodshare.util.constant.NotificationChannel;
import com.datn.foodshare.util.constant.PostStatus;
import com.datn.foodshare.util.constant.PostType;
import com.datn.foodshare.util.constant.Role;
import com.datn.foodshare.util.constant.PaymentMethod;
import com.datn.foodshare.util.constant.TransactionStatus;
import com.datn.foodshare.util.error.BusinessException;
import com.datn.foodshare.util.error.PermissionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private FoodPostRepository foodPostRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FoodPostService foodPostService;

    private OrderService orderService;

    private static final Long RECIPIENT_USER_ID = 50L;
    private static final Long ORGANIZATION_USER_ID = 60L;
    private static final Long SUPPLIER_USER_ID = 10L;
    private static final Long FOOD_POST_ID = 100L;
    private static final Long BUSINESS_PROFILE_ID = 1L;
    private static final Long ORDER_ID = 200L;

    @Mock
    private com.datn.foodshare.repository.PaymentRepository paymentRepository;
    @Mock
    private com.datn.foodshare.service.payment.strategy.PaymentStrategyFactory paymentStrategyFactory;
    @Mock
    private SupplierEarningService supplierEarningService;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock
    private com.datn.foodshare.repository.BusinessProfileRepository businessProfileRepository;
    @Mock
    private com.datn.foodshare.repository.ReportRepository reportRepository;
    @Mock
    private com.datn.foodshare.repository.ReviewRepository reviewRepository;
    private PermissionService permissionService;

    @BeforeEach
    void setUp() {
        permissionService = new PermissionService(userRepository);
        orderService = new OrderService(
                orderRepository,
                foodPostRepository,
                userRepository,
                foodPostService,
                paymentRepository,
                paymentStrategyFactory,
                supplierEarningService,
                eventPublisher,
                permissionService,
                businessProfileRepository,
                reportRepository,
                reviewRepository
        );
        lenient().when(businessProfileRepository.findByUserId(ORGANIZATION_USER_ID))
                .thenReturn(Optional.of(organizationBusinessProfile(com.datn.foodshare.util.constant.VerificationStatus.VERIFIED)));
    }

    @Test
    void createOrder_success_recipient() throws PermissionException {
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
            OrderResponse response = orderService.createOrder(request);

            assertNotNull(response);
            assertEquals(OrderStatus.PENDING, response.getOrderStatus());
            assertEquals(new BigDecimal("0"), response.getTotalAmount());
            assertEquals(1, response.getOrderDetails().size());
            assertEquals(1, response.getOrderDetails().get(0).getQuantity());
            assertEquals(BigDecimal.ZERO, response.getOrderDetails().get(0).getUnitPrice());
            assertEquals(new BigDecimal("0"), response.getOrderDetails().get(0).getSubtotal());
            assertNotNull(response.getOrderCode());
            assertTrue(response.getOrderCode().startsWith("ORD-"));

            verify(orderRepository).save(any(Order.class));
            verify(foodPostService).decreaseQuantity(FOOD_POST_ID, 1);
        }
    }

    @Test
    void createOrder_rejectsRecipientRequestingMultiplePortions() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
            when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(availableFoodPost()));
            CreateOrderRequest request = validCreateOrderRequest();
            request.setQuantity(2);

            assertThrows(BusinessException.class, () -> orderService.createOrder(request));

            verify(orderRepository, never()).save(any());
            verifyNoInteractions(foodPostService);
        }
    }

    @Test
    void createOrder_rejectsRecipientAlreadyRequestingTheSamePost() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
            when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(availableFoodPost()));
            when(orderRepository.existsByReceiverAndFoodPost(RECIPIENT_USER_ID, FOOD_POST_ID)).thenReturn(true);

            assertThrows(BusinessException.class, () -> orderService.createOrder(validCreateOrderRequest()));

            verify(orderRepository, never()).save(any());
            verifyNoInteractions(foodPostService);
        }
    }

    @Test
    void createOrder_rejectsRecipientWhenDailyFreeLimitReached() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
            when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(availableFoodPost()));
            when(orderRepository.sumFreeQuantityByReceiverBetween(any(), any(), any())).thenReturn(3L);

            assertThrows(BusinessException.class, () -> orderService.createOrder(validCreateOrderRequest()));
            verify(orderRepository, never()).save(any());
            verifyNoInteractions(foodPostService);
        }
    }

    @Test
    void createOrder_allowsRecipientUpToThreeFreePortionsPerDay() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
            when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(availableFoodPost()));
            when(orderRepository.sumFreeQuantityByReceiverBetween(any(), any(), any())).thenReturn(2L);
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            assertNotNull(orderService.createOrder(validCreateOrderRequest()));
            verify(orderRepository).save(any(Order.class));
        }
    }

    @Test
    void createOrder_paidPostIsNotSubjectToDailyFreeLimit() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
            FoodPost paid = availableFoodPost();
            paid.setPostType(PostType.PAID);
            paid.setUnitPrice(new BigDecimal("10000"));
            when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(paid));
            when(orderRepository.sumFreeQuantityByReceiverBetween(any(), any(), any())).thenReturn(3L);
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            assertNotNull(orderService.createOrder(validCreateOrderRequest()));
            verify(orderRepository).save(any(Order.class));
        }
    }

    @Test
    void createOrder_success_organization() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(ORGANIZATION_USER_ID));
            when(userRepository.findById(ORGANIZATION_USER_ID)).thenReturn(Optional.of(organizationUser()));
            when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(availableFoodPost()));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
                Order order = inv.getArgument(0);
                order.setId(ORDER_ID);
                return order;
            });

            CreateOrderRequest request = validCreateOrderRequest();
            request.setQuantity(3);
            OrderResponse response = orderService.createOrder(request);

            assertNotNull(response);
            assertEquals(OrderStatus.PENDING, response.getOrderStatus());
            verify(orderRepository).save(any(Order.class));
            verify(foodPostService).decreaseQuantity(FOOD_POST_ID, 3);
        }
    }

    @Test
    void createOrder_success_paidPost_calculatesCorrectTotalAmount() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(ORGANIZATION_USER_ID));
            when(userRepository.findById(ORGANIZATION_USER_ID)).thenReturn(Optional.of(organizationUser()));

            FoodPost paidPost = availableFoodPost();
            paidPost.setPostType(PostType.PAID);
            paidPost.setUnitPrice(new BigDecimal("15000"));
            when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(paidPost));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
                Order order = inv.getArgument(0);
                order.setId(ORDER_ID);
                return order;
            });

            CreateOrderRequest request = validCreateOrderRequest();
            request.setQuantity(5);
            OrderResponse response = orderService.createOrder(request);

            assertEquals(new BigDecimal("75000"), response.getTotalAmount());
            assertEquals(new BigDecimal("15000"), response.getOrderDetails().get(0).getUnitPrice());
            assertEquals(new BigDecimal("75000"), response.getOrderDetails().get(0).getSubtotal());
            assertEquals(5, response.getOrderDetails().get(0).getQuantity());
        }
    }

    @Test
    void createOrder_unitPriceFromFoodPost_notTrustedFromFrontend() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(ORGANIZATION_USER_ID));
            when(userRepository.findById(ORGANIZATION_USER_ID)).thenReturn(Optional.of(organizationUser()));

            FoodPost post = availableFoodPost();
            post.setPostType(PostType.PAID);
            post.setUnitPrice(new BigDecimal("20000"));
            when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(post));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
                Order order = inv.getArgument(0);
                order.setId(ORDER_ID);
                return order;
            });

            CreateOrderRequest request = validCreateOrderRequest();
            request.setQuantity(2);
            OrderResponse response = orderService.createOrder(request);

            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            Order savedOrder = orderCaptor.getValue();

            assertEquals(new BigDecimal("40000"), savedOrder.getTotalAmount());
            assertEquals(new BigDecimal("20000"), savedOrder.getOrderDetails().get(0).getUnitPrice());
        }
    }

    @Test
    void createOrder_setsReceiverNoteWhenProvided() throws PermissionException {
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
            request.setReceiverNote("Xin nhận lúc 3h chiều");
            OrderResponse response = orderService.createOrder(request);

            assertEquals("Xin nhận lúc 3h chiều", response.getReceiverNote());
        }
    }

    // ===========================
    // Authentication & Role
    // ===========================

    @Test
    void createOrder_rejectsUnauthenticatedUser() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.empty());

            assertThrows(Exception.class, () -> orderService.createOrder(validCreateOrderRequest()));
            verify(orderRepository, never()).save(any());
        }
    }

    @Test
    void createOrder_rejectsSupplierRole() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(SUPPLIER_USER_ID));
            User supplier = recipientUser();
            supplier.setId(SUPPLIER_USER_ID);
            supplier.setRole(Role.SUPPLIER);
            when(userRepository.findById(SUPPLIER_USER_ID)).thenReturn(Optional.of(supplier));

            assertThrows(PermissionException.class, () -> orderService.createOrder(validCreateOrderRequest()));
            verify(orderRepository, never()).save(any());
        }
    }

    @Test
    void createOrder_rejectsAdminRole() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(SUPPLIER_USER_ID));
            User admin = recipientUser();
            admin.setId(SUPPLIER_USER_ID);
            admin.setRole(Role.ADMIN);
            when(userRepository.findById(SUPPLIER_USER_ID)).thenReturn(Optional.of(admin));

            assertThrows(PermissionException.class, () -> orderService.createOrder(validCreateOrderRequest()));
            verify(orderRepository, never()).save(any());
        }
    }

    @Test
    void createOrder_rejectsIncompleteProfile() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            User user = recipientUser();
            user.setProfileCompleted(false);
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(user));

            assertThrows(BusinessException.class, () -> orderService.createOrder(validCreateOrderRequest()));
            verify(orderRepository, never()).save(any());
        }
    }

    // ===========================
    // FoodPost Validation
    // ===========================

    @Test
    void createOrder_rejectsNonExistentFoodPost() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
            when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.empty());

            assertThrows(BusinessException.class, () -> orderService.createOrder(validCreateOrderRequest()));
            verify(orderRepository, never()).save(any());
        }
    }

    @Test
    void createOrder_rejectsHiddenFoodPost() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
            FoodPost post = availableFoodPost();
            post.setPostStatus(PostStatus.HIDDEN);
            when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(post));

            assertThrows(BusinessException.class, () -> orderService.createOrder(validCreateOrderRequest()));
            verify(orderRepository, never()).save(any());
        }
    }

    @Test
    void createOrder_rejectsOutOfStockFoodPost() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
            FoodPost post = availableFoodPost();
            post.setPostStatus(PostStatus.OUT_OF_STOCK);
            post.setAvailableQuantity(0);
            when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(post));

            assertThrows(BusinessException.class, () -> orderService.createOrder(validCreateOrderRequest()));
            verify(orderRepository, never()).save(any());
        }
    }

    @Test
    void createOrder_rejectsDraftFoodPost() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
            FoodPost post = availableFoodPost();
            post.setPostStatus(PostStatus.DRAFT);
            when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(post));

            assertThrows(BusinessException.class, () -> orderService.createOrder(validCreateOrderRequest()));
            verify(orderRepository, never()).save(any());
        }
    }

    @Test
    void createOrder_rejectsExpiredFoodPost() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
            FoodPost post = availableFoodPost();
            post.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
            when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(post));

            assertThrows(BusinessException.class, () -> orderService.createOrder(validCreateOrderRequest()));
            verify(orderRepository, never()).save(any());
        }
    }

    @Test
    void createOrder_rejectsDeletedFoodPost() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
            FoodPost post = availableFoodPost();
            post.setPostStatus(PostStatus.DELETED);
            when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(post));

            assertThrows(BusinessException.class, () -> orderService.createOrder(validCreateOrderRequest()));
            verify(orderRepository, never()).save(any());
        }
    }

    // ===========================
    // Quantity Validation
    // ===========================

    @Test
    void createOrder_rejectsZeroQuantity() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
            when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(availableFoodPost()));

            CreateOrderRequest request = validCreateOrderRequest();
            request.setQuantity(0);

            assertThrows(BusinessException.class, () -> orderService.createOrder(request));
            verify(orderRepository, never()).save(any());
        }
    }

    @Test
    void createOrder_rejectsNegativeQuantity() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
            when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(availableFoodPost()));

            CreateOrderRequest request = validCreateOrderRequest();
            request.setQuantity(-1);

            assertThrows(BusinessException.class, () -> orderService.createOrder(request));
            verify(orderRepository, never()).save(any());
        }
    }

    @Test
    void createOrder_rejectsInsufficientQuantity() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(ORGANIZATION_USER_ID));
            when(userRepository.findById(ORGANIZATION_USER_ID)).thenReturn(Optional.of(organizationUser()));
            FoodPost post = availableFoodPost();
            post.setAvailableQuantity(2);
            when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(post));

            CreateOrderRequest request = validCreateOrderRequest();
            request.setQuantity(5);

            assertThrows(BusinessException.class, () -> orderService.createOrder(request));
            verify(orderRepository, never()).save(any());
        }
    }

    @Test
    void createOrder_acceptsExactAvailableQuantity() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(ORGANIZATION_USER_ID));
            when(userRepository.findById(ORGANIZATION_USER_ID)).thenReturn(Optional.of(organizationUser()));
            FoodPost post = availableFoodPost();
            post.setAvailableQuantity(5);
            when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(post));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
                Order order = inv.getArgument(0);
                order.setId(ORDER_ID);
                return order;
            });

            CreateOrderRequest request = validCreateOrderRequest();
            request.setQuantity(5);
            OrderResponse response = orderService.createOrder(request);

            assertNotNull(response);
            verify(foodPostService).decreaseQuantity(FOOD_POST_ID, 5);
        }
    }

    // ===========================
    // Transaction / Rollback
    // ===========================

    @Test
    void createOrder_rollsBackWhenDecreaseQuantityFails() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
            when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(availableFoodPost()));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
                Order order = inv.getArgument(0);
                order.setId(ORDER_ID);
                return order;
            });
            doThrow(new BusinessException("Không đủ số lượng. Còn lại: 0"))
                    .when(foodPostService).decreaseQuantity(FOOD_POST_ID, 1);

            assertThrows(BusinessException.class, () -> orderService.createOrder(validCreateOrderRequest()));
        }
    }

    @Test
    void createOrder_rollsBackWhenSaveFails() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
            when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(availableFoodPost()));
            when(orderRepository.save(any(Order.class))).thenThrow(new RuntimeException("DB error"));

            assertThrows(RuntimeException.class, () -> orderService.createOrder(validCreateOrderRequest()));
            verify(foodPostService, never()).decreaseQuantity(anyLong(), anyInt());
        }
    }

    // ===========================
    // Concurrent Order (Optimistic Locking)
    // ===========================

    @Test
    void createOrder_concurrentOrder_decreaseQuantityThrowsOptimisticLock() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
            when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(availableFoodPost()));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
                Order order = inv.getArgument(0);
                order.setId(ORDER_ID);
                return order;
            });
            doThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(FoodPost.class.getName(), FOOD_POST_ID))
                    .when(foodPostService).decreaseQuantity(FOOD_POST_ID, 1);

            assertThrows(org.springframework.orm.ObjectOptimisticLockingFailureException.class,
                    () -> orderService.createOrder(validCreateOrderRequest()));
        }
    }

    // ===========================
    // Order Structure Verification
    // ===========================

    @Test
    void createOrder_orderCodeIsUnique() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
            when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(availableFoodPost()));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
                Order order = inv.getArgument(0);
                order.setId(ORDER_ID);
                return order;
            });

            OrderResponse r1 = orderService.createOrder(validCreateOrderRequest());

            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
                Order order = inv.getArgument(0);
                order.setId(ORDER_ID + 1);
                return order;
            });

            OrderResponse r2 = orderService.createOrder(validCreateOrderRequest());

            assertNotEquals(r1.getOrderCode(), r2.getOrderCode());
        }
    }

    @Test
    void createOrder_orderDetailReferencesCorrectFoodPost() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
            when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(availableFoodPost()));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
                Order order = inv.getArgument(0);
                order.setId(ORDER_ID);
                return order;
            });

            OrderResponse response = orderService.createOrder(validCreateOrderRequest());

            assertEquals(FOOD_POST_ID, response.getOrderDetails().get(0).getFoodPost().getId());
            assertEquals("Bánh mì", response.getOrderDetails().get(0).getFoodPost().getName());
        }
    }

    @Test
    void createOrder_orderReferencesCorrectBusinessProfile() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
            when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(availableFoodPost()));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
                Order order = inv.getArgument(0);
                order.setId(ORDER_ID);
                return order;
            });

            OrderResponse response = orderService.createOrder(validCreateOrderRequest());

            assertEquals(BUSINESS_PROFILE_ID, response.getSupplier().getBusinessProfileId());
            assertEquals("Cửa hàng A", response.getSupplier().getName());
        }
    }

    // ===========================
    // Order Management (Get/List)
    // ===========================

    @Test
    void getMyOrders_success() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));

            Order order = new Order();
            order.setId(ORDER_ID);
            order.setReceiver(recipientUser());
            order.setBusinessProfile(businessProfile());
            order.setOrderCode("ORD-TEST1234");
            order.setOrderStatus(OrderStatus.PENDING);
            order.setTotalAmount(BigDecimal.ZERO);

            Pageable pageable = PageRequest.of(0, 10);
            Page<Order> page = new PageImpl<>(List.of(order), pageable, 1);
            
            when(orderRepository.findByReceiverId(RECIPIENT_USER_ID, pageable)).thenReturn(page);
            when(orderRepository.findAllWithDetailsByIdIn(List.of(ORDER_ID))).thenReturn(List.of(order));

            Page<OrderResponse> result = orderService.getMyOrders(pageable);
            
            assertEquals(1, result.getTotalElements());
            assertEquals(ORDER_ID, result.getContent().get(0).getId());
        }
    }

    @Test
    void getOrderDetail_success() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));

            Order order = new Order();
            order.setId(ORDER_ID);
            order.setReceiver(recipientUser());
            order.setBusinessProfile(businessProfile());
            order.setOrderStatus(OrderStatus.PENDING);
            order.setTotalAmount(BigDecimal.ZERO);

            when(orderRepository.findByIdWithDetails(ORDER_ID)).thenReturn(Optional.of(order));

            OrderResponse response = orderService.getOrderDetail(ORDER_ID);
            assertEquals(ORDER_ID, response.getId());
        }
    }

    @Test
    void getOrderDetail_rejectsOtherUser() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));

            Order order = new Order();
            order.setId(ORDER_ID);
            User otherUser = new User();
            otherUser.setId(999L);
            order.setReceiver(otherUser);

            when(orderRepository.findByIdWithDetails(ORDER_ID)).thenReturn(Optional.of(order));

            assertThrows(PermissionException.class, () -> orderService.getOrderDetail(ORDER_ID));
        }
    }

    // ===========================
    // Cancel Order
    // ===========================

    @Test
    void cancelOrder_success_restoresQuantity() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));

            Order order = new Order();
            order.setId(ORDER_ID);
            order.setReceiver(recipientUser());
            order.setBusinessProfile(businessProfile());
            order.setOrderStatus(OrderStatus.PENDING);
            order.setTotalAmount(BigDecimal.ZERO);

            FoodPost post = availableFoodPost();
            OrderDetail detail = new OrderDetail();
            detail.setFoodPost(post);
            detail.setQuantity(2);
            detail.setUnitPrice(BigDecimal.ZERO);
            order.getOrderDetails().add(detail);

            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.cancelOrder(ORDER_ID);

            assertEquals(OrderStatus.CANCELLED, response.getOrderStatus());
            assertNotNull(response.getCancelledAt());
            verify(foodPostService).restoreQuantity(FOOD_POST_ID, 2);
        }
    }

    @Test
    void cancelOrder_rejectsOtherUser() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));

            Order order = new Order();
            order.setId(ORDER_ID);
            User otherUser = new User();
            otherUser.setId(999L);
            order.setReceiver(otherUser);

            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

            assertThrows(PermissionException.class, () -> orderService.cancelOrder(ORDER_ID));
            verify(foodPostService, never()).restoreQuantity(anyLong(), anyInt());
        }
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"PENDING", "CANCELLED"})
    void cancelOrder_rejectsEveryStatusExceptPending(OrderStatus currentStatus) {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));

            Order order = new Order();
            order.setId(ORDER_ID);
            order.setReceiver(recipientUser());
            order.setOrderStatus(currentStatus);

            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

            assertThrows(BusinessException.class, () -> orderService.cancelOrder(ORDER_ID));
            verify(foodPostService, never()).restoreQuantity(anyLong(), anyInt());
        }
    }

    // ===========================
    // Supplier & Delivery Lifecycle
    // ===========================

    @Test
    void getSupplierOrders_success() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(SUPPLIER_USER_ID));
            User supplier = recipientUser();
            supplier.setId(SUPPLIER_USER_ID);
            supplier.setRole(Role.SUPPLIER);
            BusinessProfile bp = businessProfile();
            supplier.setBusinessProfile(bp);
            when(userRepository.findById(SUPPLIER_USER_ID)).thenReturn(Optional.of(supplier));

            Order order = new Order();
            order.setId(ORDER_ID);
            order.setReceiver(recipientUser());
            order.setBusinessProfile(bp);
            order.setOrderStatus(OrderStatus.PENDING);
            order.setTotalAmount(BigDecimal.ZERO);
            order.setOrderCode("ORD-TEST");

            Pageable pageable = PageRequest.of(0, 10);
            Page<Order> page = new PageImpl<>(List.of(order), pageable, 1);
            
            when(orderRepository.searchSupplierOrders(BUSINESS_PROFILE_ID, null, null, pageable)).thenReturn(page);
            when(orderRepository.findAllWithDetailsByIdIn(List.of(ORDER_ID))).thenReturn(List.of(order));

            Page<OrderResponse> result = orderService.getSupplierOrders(null, null, pageable);
            
            assertEquals(1, result.getTotalElements());
        }
    }

    @Test
    void acceptOrder_success() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(SUPPLIER_USER_ID));
            User supplier = recipientUser();
            supplier.setId(SUPPLIER_USER_ID);
            supplier.setRole(Role.SUPPLIER);
            BusinessProfile bp = businessProfile();
            supplier.setBusinessProfile(bp);
            when(userRepository.findById(SUPPLIER_USER_ID)).thenReturn(Optional.of(supplier));

            Order order = new Order();
            order.setId(ORDER_ID);
            order.setOrderStatus(OrderStatus.PENDING);
            order.setBusinessProfile(bp);
            order.setReceiver(recipientUser());
            order.setTotalAmount(BigDecimal.ZERO);
            order.setOrderCode("ORD-TEST");

            FoodPost post = availableFoodPost();
            OrderDetail detail = new OrderDetail();
            detail.setOrder(order);
            detail.setFoodPost(post);
            detail.setQuantity(1);
            detail.setUnitPrice(BigDecimal.ZERO);
            order.getOrderDetails().add(detail);

            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.acceptOrder(ORDER_ID);
            assertEquals(OrderStatus.ACCEPTED, response.getOrderStatus());
            assertEquals(post.getPickupEndAt(), response.getPickupDeadline());
        }
    }

    @Test
    void rejectOrder_success() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(SUPPLIER_USER_ID));
            User supplier = recipientUser();
            supplier.setId(SUPPLIER_USER_ID);
            supplier.setRole(Role.SUPPLIER);
            BusinessProfile bp = businessProfile();
            supplier.setBusinessProfile(bp);
            when(userRepository.findById(SUPPLIER_USER_ID)).thenReturn(Optional.of(supplier));

            Order order = new Order();
            order.setId(ORDER_ID);
            order.setOrderStatus(OrderStatus.PENDING);
            order.setBusinessProfile(bp);
            order.setReceiver(recipientUser());
            order.setTotalAmount(BigDecimal.ZERO);
            order.setOrderCode("ORD-TEST");
            
            FoodPost post = availableFoodPost();
            OrderDetail detail = new OrderDetail();
            detail.setFoodPost(post);
            detail.setQuantity(2);
            detail.setUnitPrice(BigDecimal.ZERO);
            order.getOrderDetails().add(detail);

            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            com.datn.foodshare.domain.request.RejectOrderRequest request = new com.datn.foodshare.domain.request.RejectOrderRequest("Out of stock");
            OrderResponse response = orderService.rejectOrder(ORDER_ID, request);
            
            assertEquals(OrderStatus.REJECTED, response.getOrderStatus());
            assertEquals("Out of stock", order.getRejectionReason());
            verify(foodPostService).restoreQuantity(FOOD_POST_ID, 2);
        }
    }

    @Test
    void readyForPickupOrder_success() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(SUPPLIER_USER_ID));
            User supplier = recipientUser();
            supplier.setId(SUPPLIER_USER_ID);
            supplier.setRole(Role.SUPPLIER);
            BusinessProfile bp = businessProfile();
            supplier.setBusinessProfile(bp);
            when(userRepository.findById(SUPPLIER_USER_ID)).thenReturn(Optional.of(supplier));

            Order order = new Order();
            order.setId(ORDER_ID);
            order.setOrderStatus(OrderStatus.ACCEPTED);
            order.setBusinessProfile(bp);
            order.setReceiver(recipientUser());
            order.setTotalAmount(BigDecimal.ZERO);
            order.setOrderCode("ORD-TEST");

            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.readyForPickupOrder(ORDER_ID);
            assertEquals(OrderStatus.READY_FOR_PICKUP, response.getOrderStatus());
        }
    }

    @Test
    void deliverOrder_success() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(SUPPLIER_USER_ID));
            User supplier = recipientUser();
            supplier.setId(SUPPLIER_USER_ID);
            supplier.setRole(Role.SUPPLIER);
            BusinessProfile bp = businessProfile();
            supplier.setBusinessProfile(bp);
            when(userRepository.findById(SUPPLIER_USER_ID)).thenReturn(Optional.of(supplier));

            Order order = new Order();
            order.setId(ORDER_ID);
            order.setOrderStatus(OrderStatus.READY_FOR_PICKUP);
            order.setBusinessProfile(bp);
            order.setReceiver(recipientUser());
            order.setTotalAmount(BigDecimal.ZERO);
            order.setOrderCode("ORD-TEST");

            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.deliverOrder(ORDER_ID);
            assertEquals(OrderStatus.DELIVERED, response.getOrderStatus());
        }
    }

    @Test
    void completeOrder_success() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));

            Order order = new Order();
            order.setId(ORDER_ID);
            order.setOrderStatus(OrderStatus.DELIVERED);
            order.setReceiver(recipientUser());
            order.setBusinessProfile(businessProfile());
            order.setTotalAmount(BigDecimal.ZERO);
            order.setOrderCode("ORD-TEST");

            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.completeOrder(ORDER_ID);
            assertEquals(OrderStatus.COMPLETED, response.getOrderStatus());
        }
    }

    @Test
    void batchCreateOrders_groupsItemsForOneSupplierAndAggregatesOrder() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(ORGANIZATION_USER_ID));
            when(userRepository.findById(ORGANIZATION_USER_ID)).thenReturn(Optional.of(organizationUser()));

            FoodPost first = availableFoodPost();
            first.setId(101L);
            first.setUnitPrice(new BigDecimal("10000"));
            FoodPost second = availableFoodPost();
            second.setId(102L);
            second.setUnitPrice(new BigDecimal("15000"));
            second.setBusinessProfile(first.getBusinessProfile());
            when(foodPostRepository.findByIdWithDetails(101L))
                    .thenReturn(Optional.of(first), Optional.of(first));
            when(foodPostRepository.findByIdWithDetails(102L))
                    .thenReturn(Optional.of(second), Optional.of(second));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                order.setId(ORDER_ID);
                return order;
            });

            BatchCreateOrderRequest request = new BatchCreateOrderRequest(List.of(
                    new CreateOrderRequest(101L, 2, "first"),
                    new CreateOrderRequest(102L, 3, "second")));

            List<OrderResponse> responses = orderService.batchCreateOrders(request);

            assertEquals(1, responses.size());
            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            Order saved = captor.getValue();
            assertEquals(new BigDecimal("65000"), saved.getTotalAmount());
            assertEquals("first; second", saved.getReceiverNote());
            assertEquals(2, saved.getOrderDetails().size());
            verify(foodPostService).decreaseQuantity(101L, 2);
            verify(foodPostService).decreaseQuantity(102L, 3);
        }
    }

    @Test
    void batchCreateOrders_rejectsDuplicatePostBeforeMutatingInventory() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(ORGANIZATION_USER_ID));
            when(userRepository.findById(ORGANIZATION_USER_ID)).thenReturn(Optional.of(organizationUser()));
            FoodPost post = availableFoodPost();
            when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(post));

            BatchCreateOrderRequest request = new BatchCreateOrderRequest(List.of(
                    new CreateOrderRequest(FOOD_POST_ID, 1, null),
                    new CreateOrderRequest(FOOD_POST_ID, 1, null)));

            assertThrows(BusinessException.class, () -> orderService.batchCreateOrders(request));
            verify(orderRepository, never()).save(any());
            verifyNoInteractions(foodPostService);
        }
    }

    @Test
    void completeOrder_paidOrderRequiresSuccessfulPayment() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
            Order order = supplierOrder(OrderStatus.DELIVERED);
            order.setTotalAmount(new BigDecimal("50000"));
            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
            when(paymentRepository.existsByOrderIdAndPaymentStatus(ORDER_ID, TransactionStatus.SUCCESS))
                    .thenReturn(false);

            assertThrows(BusinessException.class, () -> orderService.completeOrder(ORDER_ID));

            assertEquals(OrderStatus.DELIVERED, order.getOrderStatus());
            assertNull(order.getCompletedAt());
            verify(orderRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Test
    void completeOrder_paidOrderWithSuccessfulPaymentSucceeds() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
            Order order = supplierOrder(OrderStatus.DELIVERED);
            order.setTotalAmount(new BigDecimal("50000"));
            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
            when(paymentRepository.existsByOrderIdAndPaymentStatus(ORDER_ID, TransactionStatus.SUCCESS))
                    .thenReturn(true);
            when(orderRepository.save(order)).thenReturn(order);

            OrderResponse response = orderService.completeOrder(ORDER_ID);

            assertEquals(OrderStatus.COMPLETED, response.getOrderStatus());
            assertNotNull(order.getCompletedAt());
            verify(supplierEarningService).recordForCompletedOrder(order);
        }
    }

    @Test
    void completeOrder_withActiveDisputeRejectsCompletion() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
            Order order = supplierOrder(OrderStatus.DELIVERED);
            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.hasActiveReport(eq(ORDER_ID), any(), any())).thenReturn(true);

            assertThrows(BusinessException.class, () -> orderService.completeOrder(ORDER_ID));

            assertEquals(OrderStatus.DELIVERED, order.getOrderStatus());
            verify(orderRepository, never()).save(any());
            verifyNoInteractions(supplierEarningService);
        }
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"PENDING", "ACCEPTED"})
    void acceptOrder_rejectsEveryStatusExceptPending(OrderStatus currentStatus) {
        try (MockedStatic<SecurityUtil> su = authenticatedSupplier()) {
            Order order = supplierOrder(currentStatus);
            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

            assertThrows(BusinessException.class, () -> orderService.acceptOrder(ORDER_ID));

            verify(orderRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"ACCEPTED", "READY_FOR_PICKUP"})
    void readyForPickupOrder_rejectsEveryStatusExceptAccepted(OrderStatus currentStatus) {
        try (MockedStatic<SecurityUtil> su = authenticatedSupplier()) {
            when(orderRepository.findByIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(supplierOrder(currentStatus)));

            assertThrows(BusinessException.class, () -> orderService.readyForPickupOrder(ORDER_ID));

            verify(orderRepository, never()).save(any());
        }
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"READY_FOR_PICKUP", "DELIVERED"})
    void deliverOrder_rejectsEveryStatusExceptReadyForPickup(OrderStatus currentStatus) {
        try (MockedStatic<SecurityUtil> su = authenticatedSupplier()) {
            when(orderRepository.findByIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(supplierOrder(currentStatus)));

            assertThrows(BusinessException.class, () -> orderService.deliverOrder(ORDER_ID));

            verify(orderRepository, never()).save(any());
        }
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"DELIVERED", "COMPLETED"})
    void completeOrder_rejectsEveryStatusExceptDelivered(OrderStatus currentStatus) {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
            Order order = supplierOrder(currentStatus);
            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

            assertThrows(BusinessException.class, () -> orderService.completeOrder(ORDER_ID));

            verify(orderRepository, never()).save(any());
        }
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"PENDING", "REJECTED"})
    void rejectOrder_rejectsEveryStatusExceptPending(OrderStatus currentStatus) {
        try (MockedStatic<SecurityUtil> su = authenticatedSupplier()) {
            when(orderRepository.findByIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(supplierOrder(currentStatus)));

            var request = new com.datn.foodshare.domain.request.RejectOrderRequest("reason");
            assertThrows(BusinessException.class, () -> orderService.rejectOrder(ORDER_ID, request));

            verify(orderRepository, never()).save(any());
            verify(foodPostService, never()).restoreQuantity(anyLong(), anyInt());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Test
    void acceptOrder_whenOrderBelongsToAnotherSupplier_rejects() {
        try (MockedStatic<SecurityUtil> su = authenticatedSupplier()) {
            Order order = supplierOrder(OrderStatus.PENDING);
            order.getBusinessProfile().setId(999L);
            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

            assertThrows(PermissionException.class, () -> orderService.acceptOrder(ORDER_ID));

            verify(orderRepository, never()).save(any());
        }
    }

    @Test
    void cancelOrder_whenPaymentSucceeded_refundsUsingMatchingStrategy() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
            Order order = supplierOrder(OrderStatus.PENDING);
            Payment payment = new Payment();
            payment.setMethod(PaymentMethod.MOMO);
            payment.setPaymentStatus(TransactionStatus.SUCCESS);
            var strategy = mock(com.datn.foodshare.service.payment.strategy.PaymentStrategy.class);
            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);
            when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(payment));
            when(paymentStrategyFactory.getStrategy(PaymentMethod.MOMO)).thenReturn(strategy);
            when(strategy.processRefund(payment)).thenReturn(payment);

            orderService.cancelOrder(ORDER_ID);

            verify(strategy).processRefund(payment);
            verify(paymentRepository).save(payment);
        }
    }

    @ParameterizedTest
    @EnumSource(value = TransactionStatus.class, names = {"PENDING", "PROCESSING"})
    void cancelOrder_cancelsActivePayment(TransactionStatus paymentStatus) throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
            Order order = supplierOrder(OrderStatus.PENDING);
            Payment payment = new Payment();
            payment.setMethod(PaymentMethod.MOMO);
            payment.setPaymentStatus(paymentStatus);
            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);
            when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(payment));
            when(paymentRepository.save(payment)).thenReturn(payment);

            orderService.cancelOrder(ORDER_ID);

            assertEquals(TransactionStatus.CANCELLED, payment.getPaymentStatus());
            verify(paymentRepository).save(payment);
            verifyNoInteractions(paymentStrategyFactory);
        }
    }

    @Test
    void createOrder_rejectsPostAfterPickupWindow() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
            FoodPost post = availableFoodPost();
            post.setPickupEndAt(Instant.now().minus(1, ChronoUnit.MINUTES));
            when(foodPostRepository.findByIdWithDetails(FOOD_POST_ID)).thenReturn(Optional.of(post));

            assertThrows(BusinessException.class, () -> orderService.createOrder(validCreateOrderRequest()));

            verify(orderRepository, never()).save(any());
            verify(foodPostService, never()).decreaseQuantity(anyLong(), anyInt());
        }
    }

    @Test
    void acceptOrder_rejectsPostAfterPickupWindow() {
        try (MockedStatic<SecurityUtil> su = authenticatedSupplier()) {
            Order order = supplierOrder(OrderStatus.PENDING);
            FoodPost post = availableFoodPost();
            post.setPickupEndAt(Instant.now().minus(1, ChronoUnit.MINUTES));
            addOrderDetail(order, post, 1);
            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

            assertThrows(BusinessException.class, () -> orderService.acceptOrder(ORDER_ID));

            assertEquals(OrderStatus.PENDING, order.getOrderStatus());
            verify(orderRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Test
    void acceptOrder_rejectsExpiredPost() {
        try (MockedStatic<SecurityUtil> su = authenticatedSupplier()) {
            Order order = supplierOrder(OrderStatus.PENDING);
            FoodPost post = availableFoodPost();
            post.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
            addOrderDetail(order, post, 1);
            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

            assertThrows(BusinessException.class, () -> orderService.acceptOrder(ORDER_ID));

            assertEquals(OrderStatus.PENDING, order.getOrderStatus());
            verify(orderRepository, never()).save(any());
        }
    }

    @Test
    void acceptOrder_usesEarliestPickupEndAsBatchDeadline() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = authenticatedSupplier()) {
            Order order = supplierOrder(OrderStatus.PENDING);
            FoodPost laterPost = availableFoodPost();
            laterPost.setPickupEndAt(Instant.now().plus(4, ChronoUnit.HOURS));
            FoodPost earlierPost = availableFoodPost();
            earlierPost.setId(FOOD_POST_ID + 1);
            earlierPost.setPickupEndAt(Instant.now().plus(2, ChronoUnit.HOURS));
            addOrderDetail(order, laterPost, 1);
            addOrderDetail(order, earlierPost, 1);
            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);

            OrderResponse response = orderService.acceptOrder(ORDER_ID);

            assertEquals(earlierPost.getPickupEndAt(), response.getPickupDeadline());
        }
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING", "ACCEPTED", "READY_FOR_PICKUP"})
    void cancelTimedOutOrders_cancelsActiveOrderAndRestoresInventory(OrderStatus currentStatus) {
        Order order = supplierOrder(currentStatus);
        FoodPost post = availableFoodPost();
        post.setPickupEndAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        addOrderDetail(order, post, 2);
        when(orderRepository.findIdsPastFulfillmentWindow(any(), any(Instant.class)))
                .thenReturn(List.of(ORDER_ID));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.cancelTimedOutOrders();

        assertEquals(OrderStatus.CANCELLED, order.getOrderStatus());
        assertNotNull(order.getCancelledAt());
        assertNotNull(order.getCancellationReason());
        verify(foodPostService).restoreQuantity(FOOD_POST_ID, 2);
        verify(orderRepository).save(order);
        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertTrue(captor.getValue().supports(NotificationChannel.PUSH));
        assertFalse(captor.getValue().supports(NotificationChannel.EMAIL));
    }

    @Test
    void cancelTimedOutOrders_recordsExpiredFoodReason() {
        Order order = supplierOrder(OrderStatus.PENDING);
        FoodPost post = availableFoodPost();
        post.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        addOrderDetail(order, post, 1);
        when(orderRepository.findIdsPastFulfillmentWindow(any(), any(Instant.class)))
                .thenReturn(List.of(ORDER_ID));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.cancelTimedOutOrders();

        assertEquals(OrderStatus.CANCELLED, order.getOrderStatus());
        assertTrue(order.getCancellationReason().contains("hết hạn"));
        verify(foodPostService).restoreQuantity(FOOD_POST_ID, 1);
    }

    @Test
    void cancelTimedOutOrders_paidOrderRequestsEmailDelivery() {
        Order order = supplierOrder(OrderStatus.ACCEPTED);
        order.setPickupDeadline(Instant.now().minus(1, ChronoUnit.MINUTES));
        addOrderDetail(order, availableFoodPost(), 1);
        Payment payment = new Payment();
        payment.setMethod(PaymentMethod.MOMO);
        payment.setPaymentStatus(TransactionStatus.SUCCESS);
        var strategy = mock(com.datn.foodshare.service.payment.strategy.PaymentStrategy.class);
        when(orderRepository.findIdsPastFulfillmentWindow(any(), any(Instant.class)))
                .thenReturn(List.of(ORDER_ID));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(payment));
        when(paymentStrategyFactory.getStrategy(PaymentMethod.MOMO)).thenReturn(strategy);
        when(strategy.processRefund(payment)).thenReturn(payment);

        orderService.cancelTimedOutOrders();

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertTrue(captor.getValue().supports(NotificationChannel.EMAIL));
        assertTrue(captor.getValue().getContent().contains("hoàn lại"));
    }

    @Test
    void cancelTimedOutOrders_rechecksStateAfterLock() {
        Order order = supplierOrder(OrderStatus.DELIVERED);
        when(orderRepository.findIdsPastFulfillmentWindow(any(), any(Instant.class)))
                .thenReturn(List.of(ORDER_ID));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        orderService.cancelTimedOutOrders();

        assertEquals(OrderStatus.DELIVERED, order.getOrderStatus());
        verify(foodPostService, never()).restoreQuantity(anyLong(), anyInt());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void autoCompleteDeliveredOrders_after24HoursCompletesAndRecordsEarning() {
        Order order = supplierOrder(OrderStatus.DELIVERED);
        order.setDeliveredAt(Instant.now().minus(25, ChronoUnit.HOURS));
        when(orderRepository.findIdsEligibleForAutoCompletion(
                eq(OrderStatus.DELIVERED), any(Instant.class), any(), any()))
                .thenReturn(List.of(ORDER_ID));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.autoCompleteDeliveredOrders();

        assertEquals(OrderStatus.COMPLETED, order.getOrderStatus());
        assertNotNull(order.getCompletedAt());
        verify(supplierEarningService).recordForCompletedOrder(order);
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void autoCompleteDeliveredOrders_rechecksActiveDisputeAfterLock() {
        Order order = supplierOrder(OrderStatus.DELIVERED);
        order.setDeliveredAt(Instant.now().minus(25, ChronoUnit.HOURS));
        when(orderRepository.findIdsEligibleForAutoCompletion(
                eq(OrderStatus.DELIVERED), any(Instant.class), any(), any()))
                .thenReturn(List.of(ORDER_ID));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.hasActiveReport(eq(ORDER_ID), any(), any())).thenReturn(true);

        orderService.autoCompleteDeliveredOrders();

        assertEquals(OrderStatus.DELIVERED, order.getOrderStatus());
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(supplierEarningService);
    }

    @Test
    void autoCompleteDeliveredOrders_paidOrderWithoutSuccessfulPaymentStaysDelivered() {
        Order order = supplierOrder(OrderStatus.DELIVERED);
        order.setDeliveredAt(Instant.now().minus(25, ChronoUnit.HOURS));
        order.setTotalAmount(new BigDecimal("50000"));
        when(orderRepository.findIdsEligibleForAutoCompletion(
                eq(OrderStatus.DELIVERED), any(Instant.class), any(), any()))
                .thenReturn(List.of(ORDER_ID));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.existsByOrderIdAndPaymentStatus(ORDER_ID, TransactionStatus.SUCCESS))
                .thenReturn(false);

        orderService.autoCompleteDeliveredOrders();

        assertEquals(OrderStatus.DELIVERED, order.getOrderStatus());
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(supplierEarningService);
    }

    @Test
    void cancelOrder_duplicateRequestIsIdempotent() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
            Order order = supplierOrder(OrderStatus.CANCELLED);
            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

            assertEquals(OrderStatus.CANCELLED, orderService.cancelOrder(ORDER_ID).getOrderStatus());

            verify(orderRepository, never()).save(any());
            verify(foodPostService, never()).restoreQuantity(anyLong(), anyInt());
            verify(paymentRepository, never()).findByOrderId(anyLong());
        }
    }

    @Test
    void acceptOrder_duplicateRequestIsIdempotent() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = authenticatedSupplier()) {
            when(orderRepository.findByIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(supplierOrder(OrderStatus.ACCEPTED)));

            assertEquals(OrderStatus.ACCEPTED, orderService.acceptOrder(ORDER_ID).getOrderStatus());

            verify(orderRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Test
    void rejectOrder_duplicateRequestIsIdempotent() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = authenticatedSupplier()) {
            when(orderRepository.findByIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(supplierOrder(OrderStatus.REJECTED)));

            OrderResponse response = orderService.rejectOrder(
                    ORDER_ID,
                    new com.datn.foodshare.domain.request.RejectOrderRequest("duplicate"));

            assertEquals(OrderStatus.REJECTED, response.getOrderStatus());
            verify(orderRepository, never()).save(any());
            verify(foodPostService, never()).restoreQuantity(anyLong(), anyInt());
        }
    }

    @Test
    void readyForPickupOrder_duplicateRequestIsIdempotent() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = authenticatedSupplier()) {
            when(orderRepository.findByIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(supplierOrder(OrderStatus.READY_FOR_PICKUP)));

            assertEquals(
                    OrderStatus.READY_FOR_PICKUP,
                    orderService.readyForPickupOrder(ORDER_ID).getOrderStatus());

            verify(orderRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Test
    void deliverOrder_duplicateRequestIsIdempotent() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = authenticatedSupplier()) {
            when(orderRepository.findByIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(supplierOrder(OrderStatus.DELIVERED)));

            assertEquals(OrderStatus.DELIVERED, orderService.deliverOrder(ORDER_ID).getOrderStatus());

            verify(orderRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Test
    void completeOrder_duplicateRequestIsIdempotent() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
            when(orderRepository.findByIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(supplierOrder(OrderStatus.COMPLETED)));

            assertEquals(OrderStatus.COMPLETED, orderService.completeOrder(ORDER_ID).getOrderStatus());

            verify(orderRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    private MockedStatic<SecurityUtil> authenticatedSupplier() {
        MockedStatic<SecurityUtil> security = mockStatic(SecurityUtil.class);
        security.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(SUPPLIER_USER_ID));
        User supplier = recipientUser();
        supplier.setId(SUPPLIER_USER_ID);
        supplier.setRole(Role.SUPPLIER);
        supplier.setBusinessProfile(businessProfile());
        when(userRepository.findById(SUPPLIER_USER_ID)).thenReturn(Optional.of(supplier));
        return security;
    }

    private Order supplierOrder(OrderStatus status) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setOrderCode("ORD-TEST");
        order.setOrderStatus(status);
        order.setBusinessProfile(businessProfile());
        order.setReceiver(recipientUser());
        order.setTotalAmount(BigDecimal.ZERO);
        return order;
    }

    private void addOrderDetail(Order order, FoodPost post, int quantity) {
        OrderDetail detail = new OrderDetail();
        detail.setOrder(order);
        detail.setFoodPost(post);
        detail.setQuantity(quantity);
        detail.setUnitPrice(post.getUnitPrice());
        order.getOrderDetails().add(detail);
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

    private User organizationUser() {
        User user = new User();
        user.setId(ORGANIZATION_USER_ID);
        user.setRole(Role.ORGANIZATION);
        user.setProfileCompleted(true);
        user.setFullName("Organization A");
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
        request.setQuantity(1);
        return request;
    }

    private BusinessProfile organizationBusinessProfile(com.datn.foodshare.util.constant.VerificationStatus status) {
        BusinessProfile bp = new BusinessProfile();
        bp.setId(2L);
        bp.setVerificationStatus(status);
        bp.setProfileType(com.datn.foodshare.util.constant.ProfileType.ORGANIZATION);
        return bp;
    }

    @Test
    void createOrder_rejectsUnverifiedOrganization() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(ORGANIZATION_USER_ID));
            when(userRepository.findById(ORGANIZATION_USER_ID)).thenReturn(Optional.of(organizationUser()));
            when(businessProfileRepository.findByUserId(ORGANIZATION_USER_ID))
                    .thenReturn(Optional.of(organizationBusinessProfile(com.datn.foodshare.util.constant.VerificationStatus.UNVERIFIED)));

            CreateOrderRequest request = validCreateOrderRequest();
            BusinessException ex = assertThrows(BusinessException.class, () -> orderService.createOrder(request));
            assertTrue(ex.getMessage().contains("xác minh"));
            verify(orderRepository, never()).save(any());
        }
    }

    @Test
    void batchCreateOrders_rejectsUnverifiedOrganization() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(ORGANIZATION_USER_ID));
            when(userRepository.findById(ORGANIZATION_USER_ID)).thenReturn(Optional.of(organizationUser()));
            when(businessProfileRepository.findByUserId(ORGANIZATION_USER_ID))
                    .thenReturn(Optional.of(organizationBusinessProfile(com.datn.foodshare.util.constant.VerificationStatus.UNVERIFIED)));

            com.datn.foodshare.domain.request.BatchCreateOrderRequest request = new com.datn.foodshare.domain.request.BatchCreateOrderRequest(List.of(
                    new CreateOrderRequest(FOOD_POST_ID, 1, null)));
            BusinessException ex = assertThrows(BusinessException.class, () -> orderService.batchCreateOrders(request));
            assertTrue(ex.getMessage().contains("xác minh"));
            verify(orderRepository, never()).save(any());
        }
    }
}
