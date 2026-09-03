package com.datn.foodshare.domain.response;

import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.util.constant.PostStatus;
import com.datn.foodshare.util.constant.PostType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class FoodPostResponse {

    private Long id;
    private String name;
    private String description;
    private CategoryInfo category;
    private int totalQuantity;
    private int availableQuantity;
    private BigDecimal unitPrice;
    private BigDecimal originalPrice;
    private PostType postType;
    private PostStatus postStatus;
    private Instant expiresAt;
    private String pickupAddress;
    private Instant pickupStartAt;
    private Instant pickupEndAt;
    private List<String> images;
    private SupplierInfo supplier;
    private Instant createdAt;
    private Instant updatedAt;

    @Getter
    @Builder
    public static class CategoryInfo {
        private Long id;
        private String name;
    }

    @Getter
    @Builder
    public static class SupplierInfo {
        private Long businessProfileId;
        private String name;
        private String description;
    }

    public static FoodPostResponse from(FoodPost post) {
        List<String> imageUrls = post.getImages().stream()
                .map(img -> img.getImageUrl())
                .toList();

        return FoodPostResponse.builder()
                .id(post.getId())
                .name(post.getName())
                .description(post.getDescription())
                .category(CategoryInfo.builder()
                        .id(post.getCategory().getId())
                        .name(post.getCategory().getName())
                        .build())
                .totalQuantity(post.getTotalQuantity())
                .availableQuantity(post.getAvailableQuantity())
                .unitPrice(post.getUnitPrice())
                .originalPrice(post.getOriginalPrice())
                .postType(post.getPostType())
                .postStatus(post.getPostStatus())
                .expiresAt(post.getExpiresAt())
                .pickupAddress(post.getPickupAddress())
                .pickupStartAt(post.getPickupStartAt())
                .pickupEndAt(post.getPickupEndAt())
                .images(imageUrls)
                .supplier(SupplierInfo.builder()
                        .businessProfileId(post.getBusinessProfile().getId())
                        .name(post.getBusinessProfile().getName())
                        .description(post.getBusinessProfile().getDescription())
                        .build())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }
}
