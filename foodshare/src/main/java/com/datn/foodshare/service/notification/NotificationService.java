package com.datn.foodshare.service.notification;

import com.datn.foodshare.domain.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {
    Page<Notification> getUserNotifications(Long userId, Pageable pageable);
    void markAsRead(Long notificationId, Long userId);
    void markAllAsRead(Long userId);
}
