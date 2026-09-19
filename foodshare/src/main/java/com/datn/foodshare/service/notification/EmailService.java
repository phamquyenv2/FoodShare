package com.datn.foodshare.service.notification;

import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {
    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String from;

    public EmailService(@Nullable JavaMailSender mailSender,
                        @Value("${app.mail.enabled:false}") boolean enabled,
                        @Value("${app.mail.from:}") String from) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.from = from == null ? "" : from.trim();
    }

    @PostConstruct
    public void validateConfiguration() {
        if (!enabled) {
            log.info("Email delivery disabled; set MAIL_ENABLED=true to enable SMTP");
            return;
        }
        if (mailSender == null) throw new IllegalStateException("MAIL_ENABLED=true requires JavaMailSender");
        validateAddress(from, "MAIL_FROM");
    }

    /**
     * SMTP errors propagate to the notification listener; SMTP acceptance is not inbox delivery.
     * Sending is asynchronous at NotificationEventListener, after the business transaction commits.
     */
    public void sendEmail(String to, String subject, String body) {
        if (!enabled) {
            log.info("Bỏ qua gửi email vì cấu hình email đang tắt (app.mail.enabled=false). Người nhận dự kiến: {}", to);
            return;
        }
        if (mailSender == null) throw new IllegalStateException("JavaMailSender is unavailable");
        validateAddress(to, "recipient");
        if (subject == null || subject.isBlank() || subject.contains("\r") || subject.contains("\n"))
            throw new IllegalArgumentException("Email subject must be nonblank and contain no line breaks");
        if (body == null) throw new IllegalArgumentException("Email body must not be null");

        log.info("Đang gửi email qua SMTP: Từ [{}] -> Đến [{}] | Tiêu đề: [{}]", from, to.trim(), subject);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to.trim());
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
            log.info("Email đã được máy chủ SMTP tiếp nhận thành công (accepted) cho người nhận: {} | Tiêu đề: {}", to.trim(), subject);
        } catch (Exception ex) {
            log.error("Gửi email thất bại tới [{}] qua SMTP server. Chi tiết lỗi: {}", to.trim(), ex.getMessage(), ex);
            throw ex;
        }
    }

    private static void validateAddress(String value, String field) {
        if (value == null || value.isBlank() || value.contains("\r") || value.contains("\n"))
            throw new IllegalArgumentException(field + " must contain a valid email address");
        try {
            new InternetAddress(value.trim(), true).validate();
        } catch (AddressException e) {
            throw new IllegalArgumentException(field + " must contain a valid email address", e);
        }
    }
}
