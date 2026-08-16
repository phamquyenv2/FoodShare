package com.datn.foodshare.domain.request;

import com.datn.foodshare.util.constant.PostType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class UpdateFoodPostRequest {

    @Size(max = 200, message = "Tên món ăn tối đa 200 ký tự")
    private String name;

    @Size(max = 5000, message = "Mô tả tối đa 5000 ký tự")
    private String description;

    private Long categoryId;

    @Min(value = 1, message = "Số lượng phải lớn hơn 0")
    private Integer totalQuantity;

    private PostType postType;

    @DecimalMin(value = "0", message = "Giá không được âm")
    private BigDecimal unitPrice;

    private Instant expiresAt;

    @Size(max = 500, message = "Địa điểm nhận tối đa 500 ký tự")
    private String pickupAddress;

    private Instant pickupStartAt;

    private Instant pickupEndAt;

    /**
     * Full replacement of images. If null, images are not changed.
     * If provided (even empty list), images are replaced.
     */
    private List<String> images;
}
