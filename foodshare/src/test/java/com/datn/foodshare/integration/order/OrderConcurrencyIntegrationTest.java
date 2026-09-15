package com.datn.foodshare.integration.order;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.Category;
import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.request.CreateOrderRequest;
import com.datn.foodshare.domain.response.OrderResponse;
import com.datn.foodshare.integration.IntegrationTestSupport;
import com.datn.foodshare.repository.OrderRepository;
import com.datn.foodshare.service.OrderService;
import com.datn.foodshare.util.constant.PostStatus;
import com.datn.foodshare.util.constant.OrderStatus;
import com.datn.foodshare.util.constant.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class OrderConcurrencyIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void twoConcurrentOrdersForLastItem_onlyOneCommitsAndStockNeverBecomesNegative() throws Exception {
        User supplier = createUser(Role.SUPPLIER, true);
        BusinessProfile supplierProfile = createSupplierProfile(supplier);
        Category category = createCategory();
        FoodPost post = createAvailablePost(supplierProfile, category, 1);
        User firstRecipient = createUser(Role.RECIPIENT, true);
        User secondRecipient = createUser(Role.RECIPIENT, true);
        long ordersBefore = orderRepository.count();

        CountDownLatch workersReady = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> placeOrderAtTheSameTime(firstRecipient, post.getId(), workersReady, start));
            Future<Boolean> second = executor.submit(() -> placeOrderAtTheSameTime(secondRecipient, post.getId(), workersReady, start));

            assertThat(workersReady.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Boolean> results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertThat(results).containsExactlyInAnyOrder(true, false);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        FoodPost persistedPost = foodPostRepository.findById(post.getId()).orElseThrow();
        assertThat(orderRepository.count()).isEqualTo(ordersBefore + 1);
        assertThat(persistedPost.getAvailableQuantity()).isZero();
        assertThat(persistedPost.getPostStatus()).isEqualTo(PostStatus.OUT_OF_STOCK);
    }

    @Test
    void acceptAndCancelAtTheSameTime_onlyOneTransitionCommitsAndInventoryStaysConsistent() throws Exception {
        User supplier = createUser(Role.SUPPLIER, true);
        BusinessProfile supplierProfile = createSupplierProfile(supplier);
        Category category = createCategory();
        FoodPost post = createAvailablePost(supplierProfile, category, 2);
        User recipient = createUser(Role.RECIPIENT, true);

        authenticate(recipient);
        OrderResponse created;
        try {
            created = orderService.createOrder(new CreateOrderRequest(post.getId(), 1, null));
        } finally {
            clearAuthentication();
        }

        CountDownLatch workersReady = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> accept = executor.submit(
                    () -> transitionAtTheSameTime(supplier, created.getId(), true, workersReady, start));
            Future<Boolean> cancel = executor.submit(
                    () -> transitionAtTheSameTime(recipient, created.getId(), false, workersReady, start));

            assertThat(workersReady.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(accept.get(10, TimeUnit.SECONDS), cancel.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        Order persistedOrder = orderRepository.findById(created.getId()).orElseThrow();
        FoodPost persistedPost = foodPostRepository.findById(post.getId()).orElseThrow();
        assertThat(persistedOrder.getOrderStatus()).isIn(OrderStatus.ACCEPTED, OrderStatus.CANCELLED);
        if (persistedOrder.getOrderStatus() == OrderStatus.ACCEPTED) {
            assertThat(persistedPost.getAvailableQuantity()).isEqualTo(1);
        } else {
            assertThat(persistedPost.getAvailableQuantity()).isEqualTo(2);
        }
    }

    private boolean placeOrderAtTheSameTime(
            User recipient,
            Long postId,
            CountDownLatch workersReady,
            CountDownLatch start) {
        workersReady.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                return false;
            }
            authenticate(recipient);
            orderService.createOrder(new CreateOrderRequest(postId, 1, null));
            return true;
        } catch (Exception exception) {
            return false;
        } finally {
            clearAuthentication();
        }
    }

    private boolean transitionAtTheSameTime(
            User actor,
            Long orderId,
            boolean accept,
            CountDownLatch workersReady,
            CountDownLatch start) {
        workersReady.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                return false;
            }
            authenticate(actor);
            if (accept) {
                orderService.acceptOrder(orderId);
            } else {
                orderService.cancelOrder(orderId);
            }
            return true;
        } catch (Exception exception) {
            return false;
        } finally {
            clearAuthentication();
        }
    }
}
