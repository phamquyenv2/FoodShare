package com.datn.foodshare.integration.notification;

import com.datn.foodshare.service.notification.EmailService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** Real JavaMail SMTP transport against a loopback-only server; sends no external email. */
class EmailSmtpIntegrationTest {
    @Test void sendsVietnameseMailThroughRealSmtpTransport() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<String> received = executor.submit(() -> receive(server, false));
            EmailService service = service(server.getLocalPort());
            service.validateConfiguration();
            service.sendEmail("receiver@example.com", "Hoàn tiền thành công", "Bạn đã được hoàn 20.000đ.");
            String wire = received.get(5, TimeUnit.SECONDS);
            MimeMessage message = new MimeMessage(Session.getInstance(new Properties()),
                    new ByteArrayInputStream(wire.getBytes(StandardCharsets.US_ASCII)));
            assertEquals("sender@example.com", message.getFrom()[0].toString());
            assertEquals("receiver@example.com", message.getAllRecipients()[0].toString());
            assertEquals("Hoàn tiền thành công", message.getSubject());
            assertTrue(message.getContent().toString().contains("Bạn đã được hoàn 20.000đ."));
        }
    }
    @Test void smtpRejectionPropagatesAsDeliveryFailure() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<String> received = executor.submit(() -> receive(server, true));
            assertThrows(MailException.class, () -> service(server.getLocalPort())
                    .sendEmail("receiver@example.com", "Title", "Body"));
            assertEquals("", received.get(5, TimeUnit.SECONDS));
        }
    }
    private EmailService service(int port) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(InetAddress.getLoopbackAddress().getHostAddress());
        sender.setPort(port);
        sender.setDefaultEncoding("UTF-8");
        sender.getJavaMailProperties().setProperty("mail.smtp.auth", "false");
        sender.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout", "2000");
        sender.getJavaMailProperties().setProperty("mail.smtp.timeout", "2000");
        sender.getJavaMailProperties().setProperty("mail.smtp.writetimeout", "2000");
        return new EmailService(sender, true, "sender@example.com");
    }
    private String receive(ServerSocket server, boolean reject) throws Exception {
        server.setSoTimeout(5000);
        try (Socket socket = server.accept();
             BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
             PrintWriter output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII), true)) {
            socket.setSoTimeout(5000);
            output.print("220 localhost test SMTP\r\n"); output.flush();
            StringBuilder message = new StringBuilder();
            String line;
            while ((line = input.readLine()) != null) {
                if (line.startsWith("EHLO") || line.startsWith("HELO")) output.print("250 localhost\r\n");
                else if (line.startsWith("RCPT") && reject) output.print("550 mailbox rejected\r\n");
                else if (line.equals("DATA")) {
                    output.print("354 send message\r\n"); output.flush();
                    while ((line = input.readLine()) != null && !line.equals(".")) {
                        message.append(line.startsWith("..") ? line.substring(1) : line).append("\r\n");
                    }
                    output.print("250 accepted\r\n");
                } else if (line.equals("QUIT")) {
                    output.print("221 bye\r\n"); output.flush(); break;
                } else output.print("250 OK\r\n");
                output.flush();
            }
            return message.toString();
        }
    }
}
