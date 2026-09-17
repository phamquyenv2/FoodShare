package com.datn.foodshare.integration.matching;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.Category;
import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.util.constant.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MatchingRecommendationIntegrationTest extends MatchingGraphIntegrationSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void organizationReceivesNearbyAvailablePostWithScoreAndDistance() throws Exception {
        User supplier = locatedUser(Role.SUPPLIER, "10.7769000", "106.7009000");
        BusinessProfile profile = createSupplierProfile(supplier);
        User organization = locatedUser(Role.ORGANIZATION, "10.7815000", "106.7045000");
        Category category = createCategory();
        FoodPost post = createAvailablePost(profile, category, 5);
        matchingGraphSynchronizer.rebuildFromDatabase();

        mockMvc.perform(get("/api/matching/recommendations")
                        .queryParam("size", "10")
                        .header(HttpHeaders.AUTHORIZATION, bearer(organization)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem(post.getId().intValue())))
                .andExpect(jsonPath("$.data[?(@.id == %d)].matchScore".formatted(post.getId())).exists())
                .andExpect(jsonPath("$.data[?(@.id == %d)].distanceKm".formatted(post.getId())).exists());
    }

    @Test
    void supplierCannotAccessRecipientMatchingEndpoint() throws Exception {
        User supplier = locatedUser(Role.SUPPLIER, "10.7769000", "106.7009000");
        createSupplierProfile(supplier);

        mockMvc.perform(get("/api/matching/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(supplier)))
                .andExpect(status().isForbidden());
    }

    @Test
    void matchingRejectsInvalidRequestedSize() throws Exception {
        User recipient = locatedUser(Role.RECIPIENT, "10.7790000", "106.7030000");

        mockMvc.perform(get("/api/matching/recommendations")
                        .queryParam("size", "51")
                        .header(HttpHeaders.AUTHORIZATION, bearer(recipient)))
                .andExpect(status().isBadRequest());
    }


    @Autowired
    private com.datn.foodshare.service.matching.DynamicMatchingGraph graph;
    @Autowired
    private com.datn.foodshare.service.OrderService orderService;
    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactions;

    @Test
    void orderCommitRefreshesSupplyAndReceiverCapacityAndCancelRestoresBoth() throws Exception {
        User supplier = locatedUser(Role.SUPPLIER, "10.7769", "106.7009");
        BusinessProfile profile = createSupplierProfile(supplier);
        User receiver = locatedUser(Role.RECIPIENT, "10.7815", "106.7045");
        Category category = createCategory();
        FoodPost first = createAvailablePost(profile, category, 5);
        FoodPost second = createAvailablePost(profile, category, 5);
        createAvailablePost(profile, category, 5); // An unrequested post must remain after cancellation.
        matchingGraphSynchronizer.rebuildFromDatabase();
        authenticate(receiver);
        try {
            var firstOrder = orderService.createOrder(new com.datn.foodshare.domain.request.CreateOrderRequest(first.getId(), 1, null));
            org.junit.jupiter.api.Assertions.assertEquals(4, graph.getFoodPost(first.getId()).orElseThrow().availableQuantity());
            var score = graph.snapshotForCandidate(receiver.getId(), java.time.Instant.now()).posts().stream()
                    .flatMap(p -> p.scores().stream()).findFirst().orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(1, score.activeOrderCount());
            orderService.createOrder(new com.datn.foodshare.domain.request.CreateOrderRequest(second.getId(), 1, null));
            org.junit.jupiter.api.Assertions.assertTrue(graph.snapshotForCandidate(receiver.getId(), java.time.Instant.now()).posts()
                    .stream().allMatch(p -> p.scores().isEmpty()));
            orderService.cancelOrder(firstOrder.getId());
            org.junit.jupiter.api.Assertions.assertEquals(5, graph.getFoodPost(first.getId()).orElseThrow().availableQuantity());
            org.junit.jupiter.api.Assertions.assertTrue(graph.snapshotForCandidate(receiver.getId(), java.time.Instant.now()).posts()
                    .stream().anyMatch(p -> !p.scores().isEmpty()));
        } finally { clearAuthentication(); }
    }

    @Test
    void actualDatabaseRollbackLeavesSupplyAndGraphUnchanged() throws Exception {
        User supplier = locatedUser(Role.SUPPLIER, "10.7769", "106.7009");
        BusinessProfile profile = createSupplierProfile(supplier);
        User receiver = locatedUser(Role.RECIPIENT, "10.7815", "106.7045");
        FoodPost post = createAvailablePost(profile, createCategory(), 5);
        matchingGraphSynchronizer.rebuildFromDatabase();
        authenticate(receiver);
        try {
            new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
                try { orderService.createOrder(new com.datn.foodshare.domain.request.CreateOrderRequest(post.getId(), 1, null)); }
                catch (Exception e) { throw new RuntimeException(e); }
                status.setRollbackOnly();
            });
            org.junit.jupiter.api.Assertions.assertEquals(5, foodPostRepository.findById(post.getId()).orElseThrow().getAvailableQuantity());
            org.junit.jupiter.api.Assertions.assertEquals(5, graph.getFoodPost(post.getId()).orElseThrow().availableQuantity());
            var score = graph.snapshotForCandidate(receiver.getId(), java.time.Instant.now()).posts().stream()
                    .flatMap(p -> p.scores().stream()).findFirst().orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(0, score.activeOrderCount());
        } finally { clearAuthentication(); }
    }


    @Test
    void apiModesSelectBestScoreOrNearestExpiryAndRejectUnknownMode() throws Exception {
        User nearSupplier = locatedUser(Role.SUPPLIER, "10.7769", "106.7009");
        User farSupplier = locatedUser(Role.SUPPLIER, "10.8500", "106.7009");
        User receiver = locatedUser(Role.RECIPIENT, "10.7769", "106.7009");
        Category category = createCategory();
        FoodPost near = createAvailablePost(createSupplierProfile(nearSupplier), category, 5);
        FoodPost far = createAvailablePost(createSupplierProfile(farSupplier), category, 5);
        far.setExpiresAt(java.time.Instant.now().plusSeconds(3600));
        foodPostRepository.saveAndFlush(far);
        graph.rebuild(java.util.List.of(near, far), java.util.List.of(receiver), java.util.Map.of());

        mockMvc.perform(get("/api/matching/recommendations").queryParam("size", "1")
                        .queryParam("mode", "BEST_MATCH").header(HttpHeaders.AUTHORIZATION, bearer(receiver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(near.getId().intValue()));
        mockMvc.perform(get("/api/matching/recommendations").queryParam("size", "1")
                        .queryParam("mode", "URGENT").header(HttpHeaders.AUTHORIZATION, bearer(receiver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(far.getId().intValue()));
        mockMvc.perform(get("/api/matching/recommendations").queryParam("mode", "UNKNOWN")
                        .header(HttpHeaders.AUTHORIZATION, bearer(receiver)))
                .andExpect(status().isBadRequest());
    }

    private User locatedUser(Role role, String latitude, String longitude) {
        User user = createUser(role, true);
        user.setLatitude(new BigDecimal(latitude));
        user.setLongitude(new BigDecimal(longitude));
        return userRepository.saveAndFlush(user);
    }
}