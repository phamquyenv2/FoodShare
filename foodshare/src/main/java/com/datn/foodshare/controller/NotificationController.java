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
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @org.springframework.beans.factory.annotation.Value("${app.notification.page-size:15}")
    private int defaultPageSize;

    @GetMapping
    @ApiMessage("Lấy danh sách thông báo thành công")
    public ResponseEntity<Page<Notification>> getNotifications(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", required = false) Integer size) {
        
        int actualSize = size != null ? size : defaultPageSize;
        Pageable pageable = PageRequest.of(page, actualSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        
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
