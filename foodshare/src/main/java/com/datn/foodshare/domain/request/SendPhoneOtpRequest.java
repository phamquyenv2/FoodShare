package com.datn.foodshare.domain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SendPhoneOtpRequest(
        @NotBlank(message = "Số điện thoại không được để trống")
        @Size(max = 12, message = "Số điện thoại không được vượt quá 12 ký tự")
        @Pattern(regexp = "^(?:\\+84|0)(3|5|7|8|9)[0-9]{8}$",
                message = "Số điện thoại không đúng định dạng")
        String phone) {
}
