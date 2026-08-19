package com.datn.foodshare.controller.admin;

import com.datn.foodshare.domain.response.FoodPostResponse;
import com.datn.foodshare.service.FoodPostService;
import com.datn.foodshare.util.annotation.ApiMessage;
import com.datn.foodshare.util.constant.PostStatus;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/food-posts")
@Secured("ROLE_ADMIN")
@RequiredArgsConstructor
public class AdminFoodPostController {

    private final FoodPostService foodPostService;

    @GetMapping
    @ApiMessage("Lấy danh sách bài đăng (Admin) thành công")
    public ResponseEntity<Page<FoodPostResponse>> getAll(
            @RequestParam(required = false) PostStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(foodPostService.adminGetAll(status, pageable));
    }

    @GetMapping("/{id}")
    @ApiMessage("Lấy chi tiết bài đăng (Admin) thành công")
    public ResponseEntity<FoodPostResponse> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(foodPostService.adminGetDetail(id));
    }

    @PatchMapping("/{id}/hide")
    @ApiMessage("Ẩn bài đăng vi phạm thành công")
    public ResponseEntity<FoodPostResponse> hide(@PathVariable Long id) {
        return ResponseEntity.ok(foodPostService.adminHide(id));
    }

    @PatchMapping("/{id}/restore")
    @ApiMessage("Khôi phục bài đăng thành công")
    public ResponseEntity<FoodPostResponse> restore(@PathVariable Long id) {
        return ResponseEntity.ok(foodPostService.adminRestore(id));
    }
}
