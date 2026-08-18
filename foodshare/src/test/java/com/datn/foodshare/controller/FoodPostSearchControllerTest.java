package com.datn.foodshare.controller;

import com.datn.foodshare.domain.request.FoodPostFilterRequest;
import com.datn.foodshare.security.JwtTokenProvider;
import com.datn.foodshare.service.FoodPostService;
import com.datn.foodshare.util.constant.PostType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FoodPostController.class)
@AutoConfigureMockMvc(addFilters = false)
class FoodPostSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FoodPostService foodPostService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void bindsCombinedFiltersPaginationAndSorting() throws Exception {
        when(foodPostService.getPublicList(any(FoodPostFilterRequest.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/food-posts")
                        .param("keyword", "bread")
                        .param("categoryId", "12")
                        .param("postType", "PAID")
                        .param("minPrice", "10000")
                        .param("maxPrice", "20000")
                        .param("minAvailableQuantity", "3")
                        .param("expiresFrom", "2099-01-01T00:00:00Z")
                        .param("expiresTo", "2099-01-03T00:00:00Z")
                        .param("page", "2")
                        .param("size", "5")
                        .param("sort", "unitPrice,desc"))
                .andExpect(status().isOk());

        ArgumentCaptor<FoodPostFilterRequest> filterCaptor =
                ArgumentCaptor.forClass(FoodPostFilterRequest.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(foodPostService).getPublicList(filterCaptor.capture(), pageableCaptor.capture());

        FoodPostFilterRequest filter = filterCaptor.getValue();
        assertThat(filter.getKeyword()).isEqualTo("bread");
        assertThat(filter.getCategoryId()).isEqualTo(12L);
        assertThat(filter.getPostType()).isEqualTo(PostType.PAID);
        assertThat(filter.getMinPrice()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(filter.getMaxPrice()).isEqualByComparingTo(new BigDecimal("20000"));
        assertThat(filter.getMinAvailableQuantity()).isEqualTo(3);
        assertThat(filter.getExpiresFrom()).isEqualTo(Instant.parse("2099-01-01T00:00:00Z"));
        assertThat(filter.getExpiresTo()).isEqualTo(Instant.parse("2099-01-03T00:00:00Z"));

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        assertThat(pageable.getSort().getOrderFor("unitPrice")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("unitPrice").isDescending()).isTrue();
    }
}
