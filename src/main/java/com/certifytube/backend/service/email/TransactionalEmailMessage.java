package com.certifytube.backend.service.email;

public record TransactionalEmailMessage(
        String toEmail,
        String subject,
        String plainTextBody,
        String htmlBody,
        String replyTo
) {
}
