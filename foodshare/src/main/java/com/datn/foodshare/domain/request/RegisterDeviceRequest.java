package com.datn.foodshare.domain.request;

import com.datn.foodshare.util.constant.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterDeviceRequest(
        @NotBlank(message = "FCM token không được để trống")
        @Size(max = 500, message = "FCM token không hợp lệ")
        String fcmToken,
        @NotNull(message = "Loại thiết bị không được để trống")
        DeviceType deviceType,
        @Size(max = 150, message = "Tên thiết bị không được vượt quá 150 ký tự")
        String deviceName) {
}
