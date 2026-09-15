package com.datn.foodshare.domain.request;

import com.datn.foodshare.util.constant.VerificationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateVerificationStatusRequest {
    @NotNull(message = "Trạng thái xác thực không được để trống")
    private VerificationStatus verificationStatus;
}
