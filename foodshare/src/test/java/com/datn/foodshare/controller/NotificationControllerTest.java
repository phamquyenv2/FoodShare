package com.datn.foodshare.controller;

import com.datn.foodshare.domain.entity.Notification;
import com.datn.foodshare.service.notification.NotificationService;
import com.datn.foodshare.util.SecurityUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    private NotificationController controller;
    private MockedStatic<SecurityUtil> securityUtilMock;

    @BeforeEach
    void setUp() {
        controller = new NotificationController(notificationService);
        securityUtilMock = mockStatic(SecurityUtil.class);
        securityUtilMock.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(123L));
    }

    @AfterEach
    void tearDown() {
        securityUtilMock.close();
    }

    @Test
    void getNotifications_returnsPageOfNotifications() {
        Pageable pageable = PageRequest.of(0, 15);
        Page<Notification> page = new PageImpl<>(List.of(Notification.builder().id(1L).build()));
        when(notificationService.getUserNotifications(123L, pageable)).thenReturn(page);

        ResponseEntity<Page<Notification>> response = controller.getNotifications(pageable);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(page, response.getBody());
        verify(notificationService).getUserNotifications(123L, pageable);
    }

    @Test
    void markAsRead_callsServiceAndReturns200() {
        ResponseEntity<Void> response = controller.markAsRead(10L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(notificationService).markAsRead(10L, 123L);
    }

    @Test
    void getUnreadCount_returnsCount() {
        when(notificationService.getUnreadCount(123L)).thenReturn(5L);

        ResponseEntity<Long> response = controller.getUnreadCount();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(5L, response.getBody());
    }

    @Test
    void markAllAsRead_callsServiceAndReturns200() {
        ResponseEntity<Void> response = controller.markAllAsRead();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(notificationService).markAllAsRead(123L);
    }
}
