package com.datn.foodshare.event.listener;

import com.datn.foodshare.domain.entity.Notification;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.entity.UserDevice;
import com.datn.foodshare.event.NotificationEvent;
import com.datn.foodshare.repository.NotificationRepository;
import com.datn.foodshare.repository.UserDeviceRepository;
import com.datn.foodshare.service.notification.EmailService;
import com.datn.foodshare.service.notification.FCMService;
import com.datn.foodshare.util.constant.NotificationChannel;
import com.datn.foodshare.util.constant.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserDeviceRepository userDeviceRepository;

    @Mock
    private FCMService fcmService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private NotificationEventListener listener;

    @Captor
    private ArgumentCaptor<Notification> notificationCaptor;

    @Test
    void handleNotificationEvent_ShouldSaveToDbAndSendFCM() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@gmail.com");

        NotificationEvent event = NotificationEvent.builder()
                .source(this)
                .user(user)
                .title("Test Title")
                .content("Test Content")
                .type(NotificationType.PAYMENT)
                .channels(Set.of(NotificationChannel.IN_APP, NotificationChannel.PUSH, NotificationChannel.EMAIL))
                .build();

        UserDevice device = new UserDevice();
        device.setFcmToken("fcm-token-123");
        
        when(userDeviceRepository.findByUserIdAndIsActiveTrue(1L)).thenReturn(List.of(device));

        listener.handleNotificationEvent(event);

        verify(notificationRepository).save(notificationCaptor.capture());
        Notification savedNotification = notificationCaptor.getValue();
        assertEquals("Test Title", savedNotification.getTitle());
        assertEquals("Test Content", savedNotification.getContent());
        assertEquals(NotificationType.PAYMENT, savedNotification.getNotificationType());

        verify(fcmService).sendPushNotification(eq("fcm-token-123"), eq("Test Title"), eq("Test Content"), any());
        verify(emailService).sendEmail(eq("test@gmail.com"), eq("Test Title"), eq("Test Content"));
    }

    @Test
    void handleNotificationEvent_inAppOnly_skipsPushAndEmail() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@gmail.com");
        NotificationEvent event = NotificationEvent.builder()
                .source(this)
                .user(user)
                .title("In-app only")
                .content("No external delivery")
                .type(NotificationType.SYSTEM)
                .build();

        listener.handleNotificationEvent(event);

        verify(notificationRepository).save(any(Notification.class));
        verifyNoInteractions(userDeviceRepository, fcmService, emailService);
    }

    @Test
    void pushDeliveryFailureDoesNotPreventEmailDelivery() {
        User user = new User();
        user.setId(1L);
        user.setEmail("receiver@example.com");
        NotificationEvent event = NotificationEvent.builder().source(this).user(user)
                .title("Title").content("Body").type(NotificationType.SYSTEM)
                .channels(Set.of(NotificationChannel.PUSH, NotificationChannel.EMAIL)).build();
        UserDevice device = new UserDevice();
        device.setFcmToken("token");
        when(userDeviceRepository.findByUserIdAndIsActiveTrue(1L)).thenReturn(List.of(device));
        org.mockito.Mockito.doThrow(new IllegalStateException("Push unavailable")).when(fcmService)
                .sendPushNotification(eq("token"), eq("Title"), eq("Body"), any());

        listener.handleNotificationEvent(event);

        verify(emailService).sendEmail("receiver@example.com", "Title", "Body");
        verify(notificationRepository).save(any(Notification.class));
    }

}
