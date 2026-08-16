package com.datn.foodshare.domain.request;

import com.datn.foodshare.util.constant.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

    @NotBlank(message = "Phone không được để trống")
    @Size(max = 12, message = "Phone không được vượt quá 12 ký tự")
    @Pattern(
            regexp = "^(?:\\+84|0)(3|5|7|8|9)[0-9]{8}$",
            message = "Số điện thoại không đúng định dạng")
    private String phone;

    @NotBlank(message = "Password không được để trống")
    @Size(min = 8, max = 72, message = "Password phải có từ 8 đến 72 ký tự")
    private String password;

    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 100, message = "Họ tên không được vượt quá 100 ký tự")
    private String fullName;

    @Email(message = "Email không đúng định dạng")
    @Size(max = 100, message = "Email không được vượt quá 100 ký tự")
    private String email;

    @NotNull(message = "Role không được để trống")
    private Role role;
}
