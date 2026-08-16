package com.datn.foodshare.controller;

import com.datn.foodshare.domain.request.UpdateProfileRequest;
import com.datn.foodshare.domain.request.UpdateUserRequest;
import com.datn.foodshare.domain.response.CurrentUserResponse;
import com.datn.foodshare.service.UserService;
import com.datn.foodshare.util.annotation.ApiMessage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @ApiMessage("Lấy thông tin người dùng thành công")
    public ResponseEntity<CurrentUserResponse> getCurrentUser() {
        return ResponseEntity.ok().body(userService.getCurrentUser());
    }

    @PatchMapping
    @ApiMessage("Cập nhật thông tin người dùng thành công")
    public ResponseEntity<CurrentUserResponse> updateCurrentUser(
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok().body(userService.updateCurrentUser(request));
    }

    @PutMapping("/profile")
    @ApiMessage("Cập nhật hồ sơ thành công")
    public ResponseEntity<CurrentUserResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok().body(userService.updateProfile(request));
    }
}
