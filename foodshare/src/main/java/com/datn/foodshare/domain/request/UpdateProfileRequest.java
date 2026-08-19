package com.datn.foodshare.domain.request;

import com.datn.foodshare.util.constant.OrganizationType;
import com.datn.foodshare.util.constant.SupplierType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdateProfileRequest {

    @Pattern(
            regexp = "^(?:\\+84|0)(3|5|7|8|9)[0-9]{8}$",
            message = "Số điện thoại không đúng định dạng")
    private String phone;

    @NotBlank(message = "Địa chỉ không được để trống")
    @Size(max = 500, message = "Địa chỉ không được vượt quá 500 ký tự")
    private String specificAddress;

    @DecimalMin(value = "-90.0", message = "Latitude phải lớn hơn hoặc bằng -90")
    @DecimalMax(value = "90.0", message = "Latitude phải nhỏ hơn hoặc bằng 90")
    private BigDecimal latitude;

    @DecimalMin(value = "-180.0", message = "Longitude phải lớn hơn hoặc bằng -180")
    @DecimalMax(value = "180.0", message = "Longitude phải nhỏ hơn hoặc bằng 180")
    private BigDecimal longitude;

    @Size(max = 150, message = "Tên hồ sơ không được vượt quá 150 ký tự")
    private String name;

    @Size(max = 2000, message = "Mô tả không được vượt quá 2000 ký tự")
    private String description;

    @Size(max = 50, message = "Mã số thuế không được vượt quá 50 ký tự")
    private String taxCode;

    private SupplierType supplierType;
    private OrganizationType organizationType;
}
