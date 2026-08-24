package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.Category;
import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.OrderDetail;
import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.request.CreateOrderRequest;
import com.datn.foodshare.domain.response.OrderResponse;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

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
            assertEquals(3, response.getOrderDetails().get(0).getQuantity());
            assertEquals(BigDecimal.ZERO, response.getOrderDetails().get(0).getUnitPrice());
            assertEquals(new BigDecimal("0"), response.getOrderDetails().get(0).getSubtotal());
            assertNotNull(response.getOrderCode());
            assertTrue(response.getOrderCode().startsWith("ORD-"));

            verify(orderRepository).save(any(Order.class));
            verify(foodPostService).decreaseQuantity(FOOD_POST_ID, 3);
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
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));

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
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));

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
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
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
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));
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
                    .when(foodPostService).decreaseQuantity(FOOD_POST_ID, 3);

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
                    .when(foodPostService).decreaseQuantity(FOOD_POST_ID, 3);

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
            order.getOrderDetails().add(detail);

            when(orderRepository.findByIdWithDetails(ORDER_ID)).thenReturn(Optional.of(order));
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

            when(orderRepository.findByIdWithDetails(ORDER_ID)).thenReturn(Optional.of(order));

            assertThrows(PermissionException.class, () -> orderService.cancelOrder(ORDER_ID));
            verify(foodPostService, never()).restoreQuantity(anyLong(), anyInt());
        }
    }

    @Test
    void cancelOrder_rejectsInvalidStatus() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(RECIPIENT_USER_ID));
            when(userRepository.findById(RECIPIENT_USER_ID)).thenReturn(Optional.of(recipientUser()));

            Order order = new Order();
            order.setId(ORDER_ID);
            order.setReceiver(recipientUser());
            order.setOrderStatus(OrderStatus.ACCEPTED);

            when(orderRepository.findByIdWithDetails(ORDER_ID)).thenReturn(Optional.of(order));

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
            
            when(orderRepository.findByBusinessProfileId(BUSINESS_PROFILE_ID, pageable)).thenReturn(page);
            when(orderRepository.findAllWithDetailsByIdIn(List.of(ORDER_ID))).thenReturn(List.of(order));

            Page<OrderResponse> result = orderService.getSupplierOrders(pageable);
            
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

            when(orderRepository.findByIdWithDetails(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.acceptOrder(ORDER_ID);
            assertEquals(OrderStatus.ACCEPTED, response.getOrderStatus());
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
            order.getOrderDetails().add(detail);

            when(orderRepository.findByIdWithDetails(ORDER_ID)).thenReturn(Optional.of(order));
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

            when(orderRepository.findByIdWithDetails(ORDER_ID)).thenReturn(Optional.of(order));
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

            when(orderRepository.findByIdWithDetails(ORDER_ID)).thenReturn(Optional.of(order));
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

            when(orderRepository.findByIdWithDetails(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.completeOrder(ORDER_ID);
            assertEquals(OrderStatus.COMPLETED, response.getOrderStatus());
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
        request.setQuantity(3);
        return request;
    }
}
