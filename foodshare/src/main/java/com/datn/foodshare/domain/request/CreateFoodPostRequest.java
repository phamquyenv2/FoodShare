package com.datn.foodshare.domain.request;

import com.datn.foodshare.util.constant.PostType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CreateFoodPostRequest {

    @NotBlank(message = "Tên món ăn không được để trống")
    @Size(max = 200, message = "Tên món ăn tối đa 200 ký tự")
    private String name;

    @Size(max = 5000, message = "Mô tả tối đa 5000 ký tự")
    private String description;

    @NotNull(message = "Danh mục không được để trống")
    private Long categoryId;

    @NotNull(message = "Số lượng không được để trống")
    @Min(value = 1, message = "Số lượng phải lớn hơn 0")
    private Integer totalQuantity;

    @NotNull(message = "Loại bài đăng không được để trống")
    private PostType postType;

    @NotNull(message = "Giá không được để trống")
    @DecimalMin(value = "0", message = "Giá không được âm")
    private BigDecimal unitPrice;

    @NotNull(message = "Thời gian hết hạn không được để trống")
    private Instant expiresAt;

    @NotBlank(message = "Địa điểm nhận không được để trống")
    @Size(max = 500, message = "Địa điểm nhận tối đa 500 ký tự")
    private String pickupAddress;

    @NotNull(message = "Thời gian bắt đầu nhận không được để trống")
    private Instant pickupStartAt;

    @NotNull(message = "Thời gian kết thúc nhận không được để trống")
    private Instant pickupEndAt;

    private List<String> images;
}
