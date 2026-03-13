package com.certifytube.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailDeliveryService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:${spring.mail.username:}}")
    private String mailFrom;

    @Value("${app.frontend-base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @Value("${app.public-base-url:http://localhost:8080}")
    private String backendBaseUrl;

    public void sendEmailVerification(String toEmail, String rawToken) {
        String verifyLink = frontendBaseUrl + "/verify-email?token=" + rawToken;
        String apiVerifyLink = backendBaseUrl + "/api/auth/verify-email?token=" + rawToken;
        String subject = "Verify your CertifyTube account";
        String body = """
                Welcome to CertifyTube.

                Please verify your email by opening this link:
                %s

                If your frontend route is unavailable, you can verify directly via backend:
                %s

                If you did not create this account, ignore this email.
                """.formatted(verifyLink, apiVerifyLink);
        sendPlainText(toEmail, subject, body);
    }

    public void sendPasswordReset(String toEmail, String rawToken) {
        String resetLink = frontendBaseUrl + "/reset-password?token=" + rawToken;
        String subject = "Reset your CertifyTube password";
        String body = """
                We received a request to reset your CertifyTube password.

                Reset link:
                %s

                This link expires soon. If you did not request this, ignore this email.
                """.formatted(resetLink);
        sendPlainText(toEmail, subject, body);
    }

    private void sendPlainText(String toEmail, String subject, String body) {
        if (mailFrom == null || mailFrom.isBlank()) {
            throw new IllegalStateException("Mail sender is not configured (app.mail.from / spring.mail.username)");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(body);

        try {
            mailSender.send(message);
            log.info("Sent email to {}", toEmail);
        } catch (MailException e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
            throw new IllegalStateException("Unable to send email at this time");
        }
    }
}
