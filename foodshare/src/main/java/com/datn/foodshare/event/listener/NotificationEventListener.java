package com.datn.foodshare.event.listener;

import com.datn.foodshare.domain.entity.Notification;
import com.datn.foodshare.domain.entity.UserDevice;
import com.datn.foodshare.event.NotificationEvent;
import com.datn.foodshare.repository.NotificationRepository;
import com.datn.foodshare.repository.UserDeviceRepository;
import com.datn.foodshare.service.notification.EmailService;
import com.datn.foodshare.service.notification.FCMService;
import com.datn.foodshare.util.constant.NotificationChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationRepository notificationRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final FCMService fcmService;
    private final EmailService emailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotificationEvent(NotificationEvent event) {
        log.info("Xử lý NotificationEvent bất đồng bộ cho User ID: {}", event.getUser().getId());

        try {
            Notification notification = Notification.builder()
                    .user(event.getUser())
                    .title(event.getTitle())
                    .content(event.getContent())
                    .notificationType(event.getType())
                    .referenceType(event.getReferenceType())
                    .referenceId(event.getReferenceId())
                    .isRead(false)
                    .build();
            notificationRepository.save(notification);

            if (event.supports(NotificationChannel.PUSH)) {
                List<UserDevice> activeDevices = userDeviceRepository.findByUserIdAndIsActiveTrue(event.getUser().getId());
                Map<String, String> data = new HashMap<>();
                if (event.getReferenceType() != null) data.put("referenceType", event.getReferenceType().name());
                if (event.getReferenceId() != null) data.put("referenceId", event.getReferenceId().toString());

                for (UserDevice device : activeDevices) {
                    if (device.getFcmToken() != null && !device.getFcmToken().isBlank()) {
                        try {
                            fcmService.sendPushNotification(device.getFcmToken(), event.getTitle(), event.getContent(), data);
                        } catch (RuntimeException exception) {
                            log.error("Push delivery failed for user {}; continuing other channels", event.getUser().getId(), exception);
                        }
                    }
                }
            }

            if (event.supports(NotificationChannel.EMAIL)) {
                String recipientEmail = (event.getUser() != null) ? event.getUser().getEmail() : null;
                Long userId = (event.getUser() != null) ? event.getUser().getId() : null;
                if (recipientEmail != null && !recipientEmail.isBlank()) {
                    log.info("Chuyển tiếp gửi email thông báo cho User ID: {} | Địa chỉ nhận: {} | Tiêu đề: {}", userId, recipientEmail, event.getTitle());
                    emailService.sendEmail(recipientEmail, event.getTitle(), event.getContent());
                } else {
                    log.warn("Bỏ qua kênh EMAIL cho User ID: {} do không tìm thấy địa chỉ email.", userId);
                }
            }

        } catch (Exception e) {
            log.error("Lỗi khi xử lý thông báo cho User ID: {}. Lỗi: {}", event.getUser().getId(), e.getMessage());
        }
    }
}
