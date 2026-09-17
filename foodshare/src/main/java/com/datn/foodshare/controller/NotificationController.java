package com.datn.foodshare.controller;

import com.datn.foodshare.util.annotation.ApiMessage;
import com.datn.foodshare.domain.entity.Notification;
import com.datn.foodshare.service.notification.NotificationService;
import com.datn.foodshare.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @ApiMessage("Lấy danh sách thông báo thành công")
    public ResponseEntity<Page<Notification>> getNotifications(
            @PageableDefault(size = 15, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long userId = SecurityUtil.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(notificationService.getUserNotifications(userId, pageable));
    }

    @PatchMapping("/{id}/read")
    @ApiMessage("Đánh dấu đã đọc thành công")
    public ResponseEntity<Void> markAsRead(@PathVariable(name = "id") Long id) {
        Long userId = SecurityUtil.getCurrentUserId().orElseThrow();
        notificationService.markAsRead(id, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/unread-count")
    @ApiMessage("Lấy số lượng thông báo chưa đọc thành công")
    public ResponseEntity<Long> getUnreadCount() {
        Long userId = SecurityUtil.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(notificationService.getUnreadCount(userId));
    }

    @PatchMapping("/read-all")
    @ApiMessage("Đánh dấu tất cả đã đọc thành công")
    public ResponseEntity<Void> markAllAsRead() {
        Long userId = SecurityUtil.getCurrentUserId().orElseThrow();
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok().build();
    }
}
