package com.datn.foodshare.integration.security;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.Category;
import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.integration.IntegrationTestSupport;
import com.datn.foodshare.util.constant.PostStatus;
import com.datn.foodshare.util.constant.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FoodPostModerationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void supplierCanRestoreSelfHiddenPostButCannotRestoreAdminHiddenPost() throws Exception {
        User supplier = createUser(Role.SUPPLIER, true);
        BusinessProfile profile = createSupplierProfile(supplier);
        Category category = createCategory();
        FoodPost post = createAvailablePost(profile, category, 5);
        User admin = createUser(Role.ADMIN, true);

        mockMvc.perform(patch("/api/food-posts/{id}/hide", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(supplier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postStatus").value("HIDDEN"));
        assertThat(reload(post).isHiddenByAdmin()).isFalse();

        mockMvc.perform(patch("/api/food-posts/{id}/unhide", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(supplier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postStatus").value("AVAILABLE"));

        mockMvc.perform(patch("/api/admin/food-posts/{id}/hide", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postStatus").value("HIDDEN"));
        assertThat(reload(post).isHiddenByAdmin()).isTrue();

        mockMvc.perform(patch("/api/food-posts/{id}/unhide", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(supplier)))
                .andExpect(status().isForbidden());
        assertThat(reload(post).getPostStatus()).isEqualTo(PostStatus.HIDDEN);
        assertThat(reload(post).isHiddenByAdmin()).isTrue();
    }

    private FoodPost reload(FoodPost post) {
        return foodPostRepository.findById(post.getId()).orElseThrow();
    }
}
