package com.datn.foodshare.integration.workflow;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.Category;
import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.integration.IntegrationTestSupport;
import com.datn.foodshare.repository.OrderRepository;
import com.datn.foodshare.util.constant.OrderStatus;
import com.datn.foodshare.util.constant.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BackendWorkflowIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void supplierPublishesFood_recipientOrders_andBothCompleteOrderLifecycle() throws Exception {
        User supplier = createUser(Role.SUPPLIER, true);
        BusinessProfile supplierProfile = createSupplierProfile(supplier);
        User recipient = createUser(Role.RECIPIENT, true);
        Category category = createCategory();
        String postName = "QuyenPA Workflow meal " + UUID.randomUUID();
        Instant now = Instant.now();

        MvcResult postResult = mockMvc.perform(post("/api/food-posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(supplier))
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"%s",
                                  "categoryId":%d,
                                  "totalQuantity":5,
                                  "postType":"FREE",
                                  "unitPrice":0,
                                  "expiresAt":"%s",
                                  "pickupAddress":"123 QuyenPA Street, Hanoi",
                                  "pickupStartAt":"%s",
                                  "pickupEndAt":"%s",
                                  "isDraft":false
                                }
                                """.formatted(postName, category.getId(), now.plusSeconds(86_400),
                                now.plusSeconds(3_600), now.plusSeconds(7_200))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.postStatus").value("AVAILABLE"))
                .andReturn();
        long postId = dataId(postResult);

        mockMvc.perform(get("/api/food-posts")
                        .queryParam("keyword", postName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(postId));

        MvcResult orderResult = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(recipient))
                        .contentType("application/json")
                        .content("""
                                  {"foodPostId":%d,"quantity":1,"receiverNote":"QuyenPA workflow order"}
                                """.formatted(postId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.orderStatus").value("PENDING"))
                .andReturn();
        long orderId = dataId(orderResult);

        transition(orderId, "accept", bearer(supplier), "ACCEPTED");
        transition(orderId, "ready", bearer(supplier), "READY_FOR_PICKUP");
        transition(orderId, "deliver", bearer(supplier), "DELIVERED");
        transition(orderId, "complete", bearer(recipient), "COMPLETED");

        Order order = orderRepository.findByIdWithDetails(orderId).orElseThrow();
        FoodPost post = foodPostRepository.findById(postId).orElseThrow();
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getReceiver().getId()).isEqualTo(recipient.getId());
        assertThat(order.getBusinessProfile().getId()).isEqualTo(supplierProfile.getId());
        assertThat(order.getOrderDetails()).singleElement()
                .satisfies(detail -> {
                    assertThat(detail.getFoodPost().getId()).isEqualTo(postId);
                     assertThat(detail.getQuantity()).isEqualTo(1);
                 });
        assertThat(post.getAvailableQuantity()).isEqualTo(4);
    }

    private void transition(long orderId, String action, String bearer, String expectedStatus) throws Exception {
        mockMvc.perform(patch("/api/orders/{id}/{action}", orderId, action)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value(expectedStatus));
    }

    private long dataId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }
}
