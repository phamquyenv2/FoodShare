package com.datn.foodshare.integration.foodpost;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.Category;
import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.integration.IntegrationTestSupport;
import com.datn.foodshare.util.constant.PostStatus;
import com.datn.foodshare.util.constant.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FoodPostOwnershipIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    private User owner;
    private User otherSupplier;
    private BusinessProfile ownerProfile;
    private Category category;

    @BeforeEach
    void setUp() {
        owner = createUser(Role.SUPPLIER, true);
        ownerProfile = createSupplierProfile(owner);
        otherSupplier = createUser(Role.SUPPLIER, true);
        createSupplierProfile(otherSupplier);
        category = createCategory();
    }

    @Test
    void ownerCanCreateReadUpdateAndSoftDeletePost_throughHttpAndDatabase() throws Exception {
        Instant now = Instant.now();
        String createJson = """
                {
                  "name":"QuyenPA Fresh Bread",
                  "description":"Created by quyenpa integration test",
                  "categoryId":%d,
                  "totalQuantity":5,
                  "postType":"FREE",
                  "unitPrice":0,
                  "expiresAt":"%s",
                  "pickupAddress":"123 QuyenPA Street, Hanoi",
                  "pickupStartAt":"%s",
                  "pickupEndAt":"%s",
                  "images":[],
                  "isDraft":false
                }
                """.formatted(category.getId(), now.plusSeconds(86_400),
                now.plusSeconds(3_600), now.plusSeconds(7_200));

        MvcResult created = mockMvc.perform(post("/api/food-posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType("application/json")
                        .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.postStatus").value("AVAILABLE"))
                .andReturn();
        long postId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(get("/api/food-posts/{id}/owner", postId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("QuyenPA Fresh Bread"));

        mockMvc.perform(patch("/api/food-posts/{id}", postId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType("application/json")
                        .content("{\"name\":\"Updated QuyenPA Bread\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated QuyenPA Bread"));

        mockMvc.perform(patch("/api/food-posts/{id}/cancel", postId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postStatus").value("DELETED"));

        FoodPost saved = foodPostRepository.findById(postId).orElseThrow();
        assertThat(saved.getName()).isEqualTo("Updated QuyenPA Bread");
        assertThat(saved.getPostStatus()).isEqualTo(PostStatus.DELETED);
        assertThat(saved.getBusinessProfile().getId()).isEqualTo(ownerProfile.getId());
    }

    @Test
    void anotherSupplierCannotReadUpdateHideOrCancelPost() throws Exception {
        FoodPost post = createAvailablePost(ownerProfile, category, 5);
        String otherBearer = bearer(otherSupplier);

        mockMvc.perform(get("/api/food-posts/{id}/owner", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, otherBearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/food-posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, otherBearer)
                        .contentType("application/json")
                        .content("{\"name\":\"Stolen QuyenPA Post\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/food-posts/{id}/hide", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, otherBearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/food-posts/{id}/cancel", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, otherBearer))
                .andExpect(status().isForbidden());

        FoodPost unchanged = foodPostRepository.findById(post.getId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo(post.getName());
        assertThat(unchanged.getPostStatus()).isEqualTo(PostStatus.AVAILABLE);
    }
}
