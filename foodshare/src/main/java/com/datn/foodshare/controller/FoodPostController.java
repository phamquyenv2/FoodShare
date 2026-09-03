package com.datn.foodshare.controller;

import com.datn.foodshare.domain.request.CreateFoodPostRequest;
import com.datn.foodshare.domain.request.FoodPostFilterRequest;
import com.datn.foodshare.domain.request.UpdateFoodPostRequest;
import com.datn.foodshare.domain.response.FoodPostResponse;
import com.datn.foodshare.service.FoodPostService;
import com.datn.foodshare.util.annotation.ApiMessage;
import com.datn.foodshare.util.error.PermissionException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/food-posts")
@RequiredArgsConstructor
public class FoodPostController {

    private final FoodPostService foodPostService;

    @GetMapping
    @ApiMessage("Lấy danh sách bài đăng thực phẩm thành công")
    public ResponseEntity<Page<FoodPostResponse>> getPublicList(
            @ModelAttribute FoodPostFilterRequest filter,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(foodPostService.getPublicList(filter, pageable));
    }

    @GetMapping("/{id}")
    @ApiMessage("Lấy chi tiết bài đăng thực phẩm thành công")
    public ResponseEntity<FoodPostResponse> getDetail(@PathVariable("id") Long id) {
        return ResponseEntity.ok(foodPostService.getDetail(id));
    }

    @GetMapping("/my")
    @Secured("ROLE_SUPPLIER")
    @ApiMessage("Lấy danh sách bài đăng của tôi thành công")
    public ResponseEntity<Page<FoodPostResponse>> getMyPosts(
            @org.springframework.web.bind.annotation.RequestParam(value = "status", required = false) com.datn.foodshare.util.constant.PostStatus status,
            @org.springframework.web.bind.annotation.RequestParam(value = "keyword", required = false) String keyword,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable)
            throws PermissionException {
        return ResponseEntity.ok(foodPostService.getMyPosts(status, keyword, pageable));
    }

    @PostMapping
    @Secured("ROLE_SUPPLIER")
    @ApiMessage("Tạo bài đăng thực phẩm thành công")
    public ResponseEntity<FoodPostResponse> create(@Valid @RequestBody CreateFoodPostRequest request)
            throws PermissionException {
        return ResponseEntity.status(HttpStatus.CREATED).body(foodPostService.create(request));
    }

    @PatchMapping("/{id}/publish")
    @Secured("ROLE_SUPPLIER")
    @ApiMessage("Đăng bài thành công")
    public ResponseEntity<FoodPostResponse> publish(@PathVariable("id") Long id) throws PermissionException {
        return ResponseEntity.ok(foodPostService.publish(id));
    }

    @PatchMapping("/{id}")
    @Secured("ROLE_SUPPLIER")
    @ApiMessage("Cập nhật bài đăng thực phẩm thành công")
    public ResponseEntity<FoodPostResponse> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateFoodPostRequest request)
            throws PermissionException {
        return ResponseEntity.ok(foodPostService.update(id, request));
    }

    @PatchMapping("/{id}/hide")
    @Secured("ROLE_SUPPLIER")
    @ApiMessage("Ẩn bài đăng thành công")
    public ResponseEntity<FoodPostResponse> hide(@PathVariable("id") Long id) throws PermissionException {
        return ResponseEntity.ok(foodPostService.hide(id));
    }

    @PatchMapping("/{id}/unhide")
    @Secured("ROLE_SUPPLIER")
    @ApiMessage("Hiện lại bài đăng thành công")
    public ResponseEntity<FoodPostResponse> unhide(@PathVariable("id") Long id) throws PermissionException {
        return ResponseEntity.ok(foodPostService.unhide(id));
    }

    @PatchMapping("/{id}/cancel")
    @Secured("ROLE_SUPPLIER")
    @ApiMessage("Hủy bài đăng thành công")
    public ResponseEntity<FoodPostResponse> cancel(@PathVariable("id") Long id) throws PermissionException {
        return ResponseEntity.ok(foodPostService.cancel(id));
    }

    @GetMapping("/{id}/owner")
    @Secured("ROLE_SUPPLIER")
    @ApiMessage("Lấy chi tiết bài đăng thành công")
    public ResponseEntity<FoodPostResponse> getDetailForOwner(@PathVariable("id") Long id) throws PermissionException {
        return ResponseEntity.ok(foodPostService.getDetailForOwner(id));
    }
}
