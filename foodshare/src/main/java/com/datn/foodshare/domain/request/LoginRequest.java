package com.datn.foodshare.domain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    @NotBlank(message = "Tài khoản không được để trống")
    private String identifier;

    @NotBlank(message = "Password không được để trống")
    private String password;
}
