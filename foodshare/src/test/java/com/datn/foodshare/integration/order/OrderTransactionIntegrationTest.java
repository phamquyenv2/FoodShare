package com.datn.foodshare.integration.order;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.Category;
import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.request.BatchCreateOrderRequest;
import com.datn.foodshare.domain.request.CreateOrderRequest;
import com.datn.foodshare.domain.response.OrderResponse;
import com.datn.foodshare.integration.IntegrationTestSupport;
import com.datn.foodshare.repository.OrderRepository;
import com.datn.foodshare.service.OrderService;
import com.datn.foodshare.util.constant.OrderStatus;
import com.datn.foodshare.util.constant.PostStatus;
import com.datn.foodshare.util.constant.Role;
import com.datn.foodshare.util.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTransactionIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void batchFailureRollsBackAllDatabaseChanges() {
        User supplier = createUser(Role.SUPPLIER, true);
        BusinessProfile supplierProfile = createSupplierProfile(supplier);
        User recipient = createUser(Role.RECIPIENT, true);
        Category category = createCategory();
        FoodPost post = createAvailablePost(supplierProfile, category, 1);
        long ordersBefore = orderRepository.count();

        BatchCreateOrderRequest request = new BatchCreateOrderRequest(List.of(
                new CreateOrderRequest(post.getId(), 1, "first item"),
                new CreateOrderRequest(post.getId(), 1, "same item again")
        ));

        authenticate(recipient);
        try {
            assertThatThrownBy(() -> orderService.batchCreateOrders(request))
                    .isInstanceOf(BusinessException.class);
        } finally {
            clearAuthentication();
        }

        FoodPost afterRollback = foodPostRepository.findById(post.getId()).orElseThrow();
        assertThat(orderRepository.count()).isEqualTo(ordersBefore);
        assertThat(afterRollback.getAvailableQuantity()).isEqualTo(1);
        assertThat(afterRollback.getPostStatus()).isEqualTo(PostStatus.AVAILABLE);
    }

    @Test
    void timedOutAcceptedOrder_isCancelledAndInventoryIsRestoredExactlyOnce() throws Exception {
        User supplier = createUser(Role.SUPPLIER, true);
        BusinessProfile supplierProfile = createSupplierProfile(supplier);
        User recipient = createUser(Role.RECIPIENT, true);
        Category category = createCategory();
        FoodPost post = createAvailablePost(supplierProfile, category, 2);

        authenticate(recipient);
        OrderResponse created;
        try {
            created = orderService.createOrder(new CreateOrderRequest(post.getId(), 1, null));
        } finally {
            clearAuthentication();
        }

        authenticate(supplier);
        try {
            orderService.acceptOrder(created.getId());
        } finally {
            clearAuthentication();
        }

        Order accepted = orderRepository.findById(created.getId()).orElseThrow();
        accepted.setPickupDeadline(Instant.now().minus(1, ChronoUnit.MINUTES));
        orderRepository.saveAndFlush(accepted);

        orderService.cancelTimedOutOrders();
        orderService.cancelTimedOutOrders();

        Order cancelled = orderRepository.findById(created.getId()).orElseThrow();
        FoodPost restoredPost = foodPostRepository.findById(post.getId()).orElseThrow();
        assertThat(cancelled.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.getCancelledAt()).isNotNull();
        assertThat(cancelled.getCancellationReason()).isNotBlank();
        assertThat(restoredPost.getAvailableQuantity()).isEqualTo(2);
        assertThat(restoredPost.getPostStatus()).isEqualTo(PostStatus.AVAILABLE);
    }

    @Test
    void expiredPendingOrder_isCancelledAndInventoryIsRestored() throws Exception {
        User supplier = createUser(Role.SUPPLIER, true);
        BusinessProfile supplierProfile = createSupplierProfile(supplier);
        User recipient = createUser(Role.RECIPIENT, true);
        Category category = createCategory();
        FoodPost post = createAvailablePost(supplierProfile, category, 2);

        authenticate(recipient);
        OrderResponse created;
        try {
            created = orderService.createOrder(new CreateOrderRequest(post.getId(), 1, null));
        } finally {
            clearAuthentication();
        }

        FoodPost persistedPost = foodPostRepository.findById(post.getId()).orElseThrow();
        persistedPost.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        foodPostRepository.saveAndFlush(persistedPost);

        orderService.cancelTimedOutOrders();

        Order cancelled = orderRepository.findById(created.getId()).orElseThrow();
        FoodPost restoredPost = foodPostRepository.findById(post.getId()).orElseThrow();
        assertThat(cancelled.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.getCancellationReason()).contains("hết hạn");
        assertThat(restoredPost.getAvailableQuantity()).isEqualTo(2);
    }

    @Test
    void timedOutReadyOrder_isCancelledAndInventoryIsRestored() throws Exception {
        User supplier = createUser(Role.SUPPLIER, true);
        BusinessProfile supplierProfile = createSupplierProfile(supplier);
        User recipient = createUser(Role.RECIPIENT, true);
        Category category = createCategory();
        FoodPost post = createAvailablePost(supplierProfile, category, 2);

        authenticate(recipient);
        OrderResponse created;
        try {
            created = orderService.createOrder(new CreateOrderRequest(post.getId(), 1, null));
        } finally {
            clearAuthentication();
        }

        authenticate(supplier);
        try {
            orderService.acceptOrder(created.getId());
            orderService.readyForPickupOrder(created.getId());
        } finally {
            clearAuthentication();
        }

        Order ready = orderRepository.findById(created.getId()).orElseThrow();
        ready.setPickupDeadline(Instant.now().minus(1, ChronoUnit.MINUTES));
        orderRepository.saveAndFlush(ready);

        orderService.cancelTimedOutOrders();

        Order cancelled = orderRepository.findById(created.getId()).orElseThrow();
        FoodPost restoredPost = foodPostRepository.findById(post.getId()).orElseThrow();
        assertThat(cancelled.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(restoredPost.getAvailableQuantity()).isEqualTo(2);
    }
}
