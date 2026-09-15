package com.datn.foodshare.service.notification;

import com.datn.foodshare.domain.entity.Notification;
import com.datn.foodshare.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Test
    void getUserNotifications_ShouldReturnPage() {
        Long userId = 1L;
        PageRequest pageRequest = PageRequest.of(0, 10);
        Notification notification = new Notification();
        Page<Notification> page = new PageImpl<>(List.of(notification));

        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageRequest)).thenReturn(page);

        Page<Notification> result = notificationService.getUserNotifications(userId, pageRequest);

        assertEquals(1, result.getTotalElements());
        verify(notificationRepository).findByUserIdOrderByCreatedAtDesc(userId, pageRequest);
    }

    @Test
    void markAsRead_ShouldCallRepository() {
        Long notificationId = 1L;
        Long userId = 2L;
        com.datn.foodshare.domain.entity.User owner = new com.datn.foodshare.domain.entity.User();
        owner.setId(userId);
        Notification notification = new Notification();
        notification.setUser(owner);
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        notificationService.markAsRead(notificationId, userId);

        assertTrue(notification.isRead());
        verify(notificationRepository).save(notification);
    }

    @Test
    void markAsRead_WhenNotificationBelongsToAnotherUser_DoesNotSave() {
        Notification notification = new Notification();
        com.datn.foodshare.domain.entity.User owner = new com.datn.foodshare.domain.entity.User();
        owner.setId(99L);
        notification.setUser(owner);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        notificationService.markAsRead(1L, 2L);

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void markAllAsRead_ShouldCallRepository() {
        Long userId = 2L;

        notificationService.markAllAsRead(userId);

        verify(notificationRepository).markAllAsReadByUserId(userId);
    }
}
