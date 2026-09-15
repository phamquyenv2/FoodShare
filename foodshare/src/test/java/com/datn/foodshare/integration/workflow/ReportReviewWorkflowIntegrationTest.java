package com.datn.foodshare.integration.workflow;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.Category;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.integration.IntegrationTestSupport;
import com.datn.foodshare.util.constant.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportReviewWorkflowIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deliveredOrderCanBeReportedAndCompletedOrderCanBeReviewed() throws Exception {
        User supplier = createUser(Role.SUPPLIER, true);
        BusinessProfile profile = createSupplierProfile(supplier);
        User recipient = createUser(Role.RECIPIENT, true);
        User admin = createUser(Role.ADMIN, true);
        Category category = createCategory();
        Instant now = Instant.now();

        MvcResult postResult = mockMvc.perform(post("/api/food-posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(supplier))
                        .contentType("application/json")
                        .content("""
                                {"name":"Report workflow meal","categoryId":%d,"totalQuantity":3,"postType":"FREE","unitPrice":0,"expiresAt":"%s","pickupAddress":"123 Integration Street","pickupStartAt":"%s","pickupEndAt":"%s","isDraft":false}
                                """.formatted(category.getId(), now.plusSeconds(86_400), now.plusSeconds(3_600), now.plusSeconds(7_200))))
                .andExpect(status().isCreated())
                .andReturn();
        long foodPostId = dataId(postResult);

        MvcResult orderResult = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(recipient))
                        .contentType("application/json")
                        .content("{\"foodPostId\":" + foodPostId + ",\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andReturn();
        long orderId = dataId(orderResult);

        transition(orderId, "accept", supplier, "ACCEPTED");
        transition(orderId, "ready", supplier, "READY_FOR_PICKUP");
        transition(orderId, "deliver", supplier, "DELIVERED");

        MvcResult reportResult = mockMvc.perform(post("/api/reports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(recipient))
                        .contentType("application/json")
                        .content("""
                                {"title":"Order issue","content":"Packaging was damaged","reportType":"FOOD_QUALITY","evidenceUrl":"  https://example.test/evidence  ","referenceType":"ORDER","referenceId":%d}
                                """.formatted(orderId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reportStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.evidenceUrl").value("https://example.test/evidence"))
                .andReturn();
        long reportId = dataId(reportResult);

        mockMvc.perform(get("/api/reports").header(HttpHeaders.AUTHORIZATION, bearer(recipient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get("/api/admin/reports/{id}", reportId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportStatus").value("REVIEWING"));
        mockMvc.perform(patch("/api/admin/reports/{id}/status", reportId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType("application/json")
                        .content("{\"reportStatus\":\"RESOLVED\",\"response\":\"Refund issued\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportStatus").value("RESOLVED"))
                .andExpect(jsonPath("$.data.resolvedAt").isNotEmpty());

        transition(orderId, "complete", recipient, "COMPLETED");
        mockMvc.perform(post("/api/reviews")
                        .header(HttpHeaders.AUTHORIZATION, bearer(recipient))
                        .contentType("application/json")
                        .content("{\"orderId\":" + orderId + ",\"rating\":5,\"comment\":\"  Great recovery  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.comment").value("Great recovery"));

        mockMvc.perform(get("/api/reviews/supplier").header(HttpHeaders.AUTHORIZATION, bearer(supplier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get("/api/reviews/business/{id}/summary", profile.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageRating").value(5.0))
                .andExpect(jsonPath("$.data.totalReviews").value(1));
    }

    private void transition(long orderId, String action, User actor, String expectedStatus) throws Exception {
        mockMvc.perform(patch("/api/orders/{id}/{action}", orderId, action)
                        .header(HttpHeaders.AUTHORIZATION, bearer(actor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value(expectedStatus));
    }

    private long dataId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }
}
