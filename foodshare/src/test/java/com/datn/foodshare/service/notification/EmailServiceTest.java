package com.datn.foodshare.service.notification;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailServiceTest {
    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final EmailService service = new EmailService(sender, true, "FoodShare <sender@example.com>");

    @Test void sendsExplicitFromAndVietnameseSubjectAndContent() {
        service.validateConfiguration();
        service.sendEmail(" receiver@example.com ", "Hoàn tiền thành công", "Bạn đã được hoàn 20.000đ.");
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertEquals("FoodShare <sender@example.com>", message.getValue().getFrom());
        assertArrayEquals(new String[]{"receiver@example.com"}, message.getValue().getTo());
        assertEquals("Hoàn tiền thành công", message.getValue().getSubject());
        assertEquals("Bạn đã được hoàn 20.000đ.", message.getValue().getText());
    }
    @Test void disabledDeliveryDoesNotContactSmtp() {
        var disabled = new EmailService(sender, false, "");
        disabled.validateConfiguration();
        disabled.sendEmail("receiver@example.com", "Title", "Body");
        verifyNoInteractions(sender);
    }
    @Test void disabledDeliveryCanStartWithoutSender() {
        var disabled = new EmailService(null, false, "");
        assertDoesNotThrow(disabled::validateConfiguration);
        assertDoesNotThrow(() -> disabled.sendEmail("receiver@example.com", "Title", "Body"));
    }
    @Test void enabledDeliveryRequiresSenderAndValidFrom() {
        assertThrows(IllegalStateException.class, () -> new EmailService(null, true, "sender@example.com").validateConfiguration());
        assertThrows(IllegalArgumentException.class, () -> new EmailService(sender, true, "").validateConfiguration());
        assertThrows(IllegalArgumentException.class, () -> new EmailService(sender, true, "invalid").validateConfiguration());
    }
    @Test void invalidRecipientsAndHeaderInjectionAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.sendEmail("invalid", "Title", "Body"));
        assertThrows(IllegalArgumentException.class, () -> service.sendEmail("receiver@example.com\r\nBcc: other@example.com", "Title", "Body"));
        assertThrows(IllegalArgumentException.class, () -> service.sendEmail("receiver@example.com", "Title\r\nBcc: other@example.com", "Body"));
        assertThrows(IllegalArgumentException.class, () -> service.sendEmail("receiver@example.com", "Title", null));
        verifyNoInteractions(sender);
    }
    @Test void smtpFailureIsObservableByCaller() {
        doThrow(new MailSendException("SMTP unavailable")).when(sender).send(any(SimpleMailMessage.class));
        assertThrows(MailSendException.class, () -> service.sendEmail("receiver@example.com", "Title", "Body"));
    }
}
