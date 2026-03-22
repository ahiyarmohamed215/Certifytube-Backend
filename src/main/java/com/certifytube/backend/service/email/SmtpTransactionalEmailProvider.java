package com.certifytube.backend.service.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.email.provider", havingValue = "smtp")
public class SmtpTransactionalEmailProvider implements TransactionalEmailProvider {

    private final JavaMailSender mailSender;

    @Value("${app.email.sender:${app.mail.from:${spring.mail.username:}}}")
    private String senderEmail;

    @Override
    public void send(TransactionalEmailMessage message) {
        long startedAt = System.nanoTime();
        String toMasked = maskEmail(message.toEmail());
        log.info("MAIL_PROVIDER_SEND_REQUEST_STARTED provider=smtp to={}", toMasked);

        String from = senderEmail == null ? "" : senderEmail.trim();
        if (from.isBlank()) {
            throw new IllegalStateException("SMTP sender email is missing (set APP_EMAIL_SENDER / app.email.sender)");
        }

        try {
            MimeMessage mail = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mail, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);

            String replyTo = message.replyTo();
            if (replyTo != null && !replyTo.isBlank()) {
                helper.setReplyTo(replyTo.trim());
            }

            helper.setTo(message.toEmail());
            helper.setSubject(message.subject());
            helper.setText(message.plainTextBody(), message.htmlBody());
            mailSender.send(mail);

            log.info("MAIL_PROVIDER_RESPONSE_SUCCESS provider=smtp to={} durationMs={}",
                    toMasked,
                    elapsedMs(startedAt));
        } catch (MessagingException | MailException ex) {
            log.error("MAIL_PROVIDER_RESPONSE_FAILURE provider=smtp to={} durationMs={} reason={}",
                    toMasked,
                    elapsedMs(startedAt),
                    ex.getMessage(),
                    ex);
            throw new IllegalStateException("Unable to send email via SMTP", ex);
        }
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "<empty>";
        }
        String trimmed = email.trim().toLowerCase();
        int at = trimmed.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return trimmed.charAt(0) + "***" + trimmed.substring(at);
    }

    private long elapsedMs(long startedAtNs) {
        return (System.nanoTime() - startedAtNs) / 1_000_000L;
    }
}
