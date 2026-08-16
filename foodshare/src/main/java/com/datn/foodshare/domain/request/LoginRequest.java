package com.datn.foodshare.domain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    @NotBlank(message = "Phone không được để trống")
    @Size(max = 10, message = "Phone không được vượt quá 10 ký tự")
    private String phone;

    @NotBlank(message = "Password không được để trống")
    private String password;
}
