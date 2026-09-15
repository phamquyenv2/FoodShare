package com.datn.foodshare.domain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @Size(max = 30, message = "Mã ngân hàng/ví không được vượt quá 30 ký tự")
    private String bankCode;

    @NotBlank(message = "Tên ngân hàng/ví không được để trống")
    @Size(max = 150, message = "Tên ngân hàng/ví không được vượt quá 150 ký tự")
    private String bankName;

    @NotBlank(message = "Số tài khoản không được để trống")
    @Size(max = 50, message = "Số tài khoản không được vượt quá 50 ký tự")
    private String accountNumber;

    @NotBlank(message = "Tên chủ tài khoản không được để trống")
    @Size(max = 150, message = "Tên chủ tài khoản không được vượt quá 150 ký tự")
    private String accountHolderName;

    private boolean isDefault;
}
