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
        if (!enabled) return;
        if (mailSender == null) throw new IllegalStateException("JavaMailSender is unavailable");
        validateAddress(to, "recipient");
        if (subject == null || subject.isBlank() || subject.contains("\r") || subject.contains("\n"))
            throw new IllegalArgumentException("Email subject must be nonblank and contain no line breaks");
        if (body == null) throw new IllegalArgumentException("Email body must not be null");

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to.trim());
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
        log.info("Notification email accepted by SMTP");
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
