package com.datn.foodshare.integration.matching;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.Category;
import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.integration.IntegrationTestSupport;
import com.datn.foodshare.service.matching.FoodPostPriorityQueue;
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

class MatchingRecommendationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FoodPostPriorityQueue foodPostPriorityQueue;

    @Test
    void organizationReceivesNearbyAvailablePostWithScoreAndDistance() throws Exception {
        User supplier = locatedUser(Role.SUPPLIER, "10.7769000", "106.7009000");
        BusinessProfile profile = createSupplierProfile(supplier);
        User organization = locatedUser(Role.ORGANIZATION, "10.7815000", "106.7045000");
        Category category = createCategory();
        FoodPost post = createAvailablePost(profile, category, 5);
        foodPostPriorityQueue.addOrUpdate(post);

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

    private User locatedUser(Role role, String latitude, String longitude) {
        User user = createUser(role, true);
        user.setLatitude(new BigDecimal(latitude));
        user.setLongitude(new BigDecimal(longitude));
        return userRepository.saveAndFlush(user);
    }
}
