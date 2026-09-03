package com.datn.foodshare.domain.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateSystemConfigRequest {
    @NotBlank(message = "Giá trị cấu hình không được để trống")
    private String configValue;
}
