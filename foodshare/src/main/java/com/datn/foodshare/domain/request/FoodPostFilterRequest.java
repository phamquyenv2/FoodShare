package com.datn.foodshare.domain.request;

import com.datn.foodshare.util.constant.PostType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
public class FoodPostFilterRequest {

    private String keyword;

    private Long categoryId;

    private PostType postType;

    private BigDecimal minPrice;

    private BigDecimal maxPrice;

    private Integer minAvailableQuantity;

    private Instant expiresFrom;

    private Instant expiresTo;
}
