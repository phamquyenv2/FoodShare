package com.datn.foodshare.domain.request;

import com.datn.foodshare.util.constant.Role;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoogleLoginRequest {

    @NotBlank(message = "Google ID Token không được để trống")
    private String idToken;

    private Role role;
}
