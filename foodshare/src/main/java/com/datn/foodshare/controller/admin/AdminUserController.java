package com.datn.foodshare.controller.admin;

import com.datn.foodshare.domain.request.UpdateUserStatusRequest;
import com.datn.foodshare.domain.response.AdminUserResponse;
import com.datn.foodshare.service.UserService;
import com.datn.foodshare.util.annotation.ApiMessage;
import com.datn.foodshare.util.constant.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@Secured("ROLE_ADMIN")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    @ApiMessage("Lấy danh sách người dùng (Admin) thành công")
    public ResponseEntity<Page<AdminUserResponse>> getAllUsers(
            @RequestParam(name = "role", required = false) Role role,
            @RequestParam(name = "active", required = false) Boolean active,
            @RequestParam(name = "verificationStatus", required = false) com.datn.foodshare.util.constant.VerificationStatus verificationStatus,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(userService.adminGetAllUsers(role, active, verificationStatus, pageable));
    }

    @GetMapping("/{id}")
    @ApiMessage("Lấy chi tiết người dùng (Admin) thành công")
    public ResponseEntity<AdminUserResponse> getUserDetail(@PathVariable(name = "id") Long id) {
        return ResponseEntity.ok(userService.adminGetUserDetail(id));
    }

    @PatchMapping("/{id}/status")
    @ApiMessage("Cập nhật trạng thái tài khoản thành công")
    public ResponseEntity<AdminUserResponse> updateUserStatus(
            @PathVariable(name = "id") Long id,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return ResponseEntity.ok(userService.adminUpdateUserStatus(id, request));
    }

    @PatchMapping("/{id}/verify")
    @ApiMessage("Xác thực hồ sơ nhà cung cấp thành công")
    public ResponseEntity<AdminUserResponse> updateUserVerificationStatus(
            @PathVariable(name = "id") Long id,
            @Valid @RequestBody com.datn.foodshare.domain.request.UpdateVerificationStatusRequest request) {
        return ResponseEntity.ok(userService.adminVerifyBusinessProfile(id, request));
    }
}
