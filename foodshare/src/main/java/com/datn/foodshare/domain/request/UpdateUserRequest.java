package com.datn.foodshare.domain.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdateUserRequest {

    @Pattern(regexp = ".*\\S.*", message = "Họ tên không được để trống")
    @Size(max = 100, message = "Họ tên không được vượt quá 100 ký tự")
    private String fullName;

    @Email(message = "Email không đúng định dạng")
    @Size(max = 100, message = "Email không được vượt quá 100 ký tự")
    private String email;

    @Size(max = 500, message = "Avatar URL không được vượt quá 500 ký tự")
    private String avatarUrl;

    @Pattern(regexp = ".*\\S.*", message = "Địa chỉ không được để trống")
    @Size(max = 500, message = "Địa chỉ không được vượt quá 500 ký tự")
    private String specificAddress;

    @DecimalMin(value = "-90.0", message = "Latitude phải lớn hơn hoặc bằng -90")
    @DecimalMax(value = "90.0", message = "Latitude phải nhỏ hơn hoặc bằng 90")
    private BigDecimal latitude;

    @DecimalMin(value = "-180.0", message = "Longitude phải lớn hơn hoặc bằng -180")
    @DecimalMax(value = "180.0", message = "Longitude phải nhỏ hơn hoặc bằng 180")
    private BigDecimal longitude;
}
