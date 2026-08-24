package com.datn.foodshare.domain.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreatePayoutAccountRequest {

    @NotBlank(message = "Mã ngân hàng/ví không được để trống")
    private String bankCode;

    @NotBlank(message = "Tên ngân hàng/ví không được để trống")
    private String bankName;

    @NotBlank(message = "Số tài khoản không được để trống")
    private String accountNumber;

    @NotBlank(message = "Tên chủ tài khoản không được để trống")
    private String accountHolderName;

    private boolean isDefault;
}
