package com.certifytube.backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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

    @Value("${app.company-name:CertifyTube}")
    private String companyName;

    @Value("${app.support-email:${app.mail.from:${spring.mail.username:}}}")
    private String supportEmail;

    @Value("${app.mail.reply-to:}")
    private String replyTo;

    @Value("${app.mail.logo-url:}")
    private String logoUrl;

    @Async("mailTaskExecutor")
    public void sendEmailVerification(String toEmail, String rawToken) {
        String frontendBase = normalizedBaseUrl(frontendBaseUrl, "app.frontend-base-url");
        String backendBase = normalizedBaseUrl(backendBaseUrl, "app.public-base-url");
        warnIfLocalBase(frontendBase, "app.frontend-base-url");
        warnIfLocalBase(backendBase, "app.public-base-url");
        String encodedToken = URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        String verifyLink = frontendBase + "/verify-email?token=" + encodedToken;
        String apiVerifyLink = backendBase + "/api/auth/verify-email?token=" + encodedToken;
        String subject = "Verify your " + companyName + " account";

        String plainText = """
                Welcome to %s.

                Please verify your email:
                %s

                Fallback direct API verification:
                %s

                If you did not create this account, ignore this email.
                """.formatted(companyName, verifyLink, apiVerifyLink);

        String html = buildHtmlMail(
                "Verify Your Email",
                "Welcome to " + escapeHtml(companyName),
                "Please confirm your email address to activate your account.",
                "Verify Email",
                verifyLink,
                "If the button doesn't work, use this link:",
                verifyLink,
                "Fallback API verification link:",
                apiVerifyLink,
                "If you did not create this account, you can safely ignore this email.");

        sendMail(toEmail, subject, plainText, html);
    }

    @Async("mailTaskExecutor")
    public void sendPasswordReset(String toEmail, String rawToken) {
        String frontendBase = normalizedBaseUrl(frontendBaseUrl, "app.frontend-base-url");
        warnIfLocalBase(frontendBase, "app.frontend-base-url");
        String encodedToken = URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        String resetLink = frontendBase + "/reset-password?token=" + encodedToken;
        String subject = "Reset your " + companyName + " password";

        String plainText = """
                We received a request to reset your %s password.

                Reset your password:
                %s

                This link expires soon. If you did not request this, ignore this email.
                """.formatted(companyName, resetLink);

        String html = buildHtmlMail(
                "Password Reset",
                "Reset your password",
                "Use the button below to set a new password for your account.",
                "Reset Password",
                resetLink,
                "If the button doesn't work, use this link:",
                resetLink,
                null,
                null,
                "This link expires soon. If you did not request this, ignore this email.");

        sendMail(toEmail, subject, plainText, html);
    }

    private void sendMail(String toEmail, String subject, String plainText, String htmlBody) {
        if (mailFrom == null || mailFrom.isBlank()) {
            throw new IllegalStateException("Mail sender is not configured (app.mail.from / spring.mail.username)");
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(mailFrom);
            if (replyTo != null && !replyTo.isBlank()) {
                helper.setReplyTo(replyTo);
            }
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(plainText, htmlBody);

            mailSender.send(message);
            log.info("Sent email to {}", toEmail);
        } catch (MessagingException | MailException e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
            throw new IllegalStateException("Unable to send email at this time");
        }
    }

    private String buildHtmlMail(
            String title,
            String heading,
            String intro,
            String buttonText,
            String buttonUrl,
            String primaryFallbackLabel,
            String primaryFallbackUrl,
            String secondaryFallbackLabel,
            String secondaryFallbackUrl,
            String footNote) {

        StringBuilder fallback = new StringBuilder();
        if (primaryFallbackLabel != null && primaryFallbackUrl != null) {
            fallback.append("<p style='margin:14px 0 0;color:#475569;font-size:13px;'>")
                    .append(escapeHtml(primaryFallbackLabel))
                    .append("<br><a href='").append(escapeHtml(primaryFallbackUrl))
                    .append("' style='color:#2563eb;'>")
                    .append(escapeHtml(primaryFallbackUrl))
                    .append("</a></p>");
        }
        if (secondaryFallbackLabel != null && secondaryFallbackUrl != null) {
            fallback.append("<p style='margin:10px 0 0;color:#475569;font-size:13px;'>")
                    .append(escapeHtml(secondaryFallbackLabel))
                    .append("<br><a href='").append(escapeHtml(secondaryFallbackUrl))
                    .append("' style='color:#2563eb;'>")
                    .append(escapeHtml(secondaryFallbackUrl))
                    .append("</a></p>");
        }

        String safeLogo = (logoUrl == null || logoUrl.isBlank()) ? "" :
                "<img src='" + escapeHtml(logoUrl) + "' alt='" + escapeHtml(companyName) +
                        "' style='height:36px;display:block;margin:0 auto 16px;' />";
        String safeSupport = (supportEmail == null || supportEmail.isBlank()) ? "" : escapeHtml(supportEmail);

        return """
                <html>
                  <body style="margin:0;padding:0;background:#f1f5f9;font-family:Segoe UI,Arial,sans-serif;">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="padding:24px 12px;">
                      <tr>
                        <td align="center">
                          <table role="presentation" width="640" cellspacing="0" cellpadding="0" style="max-width:640px;width:100%%;background:#ffffff;border-radius:12px;overflow:hidden;border:1px solid #e2e8f0;">
                            <tr>
                              <td style="background:#b91c1c;color:#ffffff;padding:18px 24px;font-size:14px;letter-spacing:.3px;">%s</td>
                            </tr>
                            <tr>
                              <td style="padding:28px 24px;">
                                %s
                                <h1 style="margin:0 0 10px;font-size:24px;color:#0f172a;">%s</h1>
                                <p style="margin:0 0 18px;color:#334155;line-height:1.6;font-size:15px;">%s</p>
                                <p style="margin:0 0 18px;">
                                  <a href="%s" style="background:#dc2626;color:#ffffff;text-decoration:none;padding:12px 18px;border-radius:8px;display:inline-block;font-weight:600;">
                                    %s
                                  </a>
                                </p>
                                %s
                                <p style="margin:18px 0 0;color:#64748b;font-size:12px;line-height:1.6;">%s</p>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:14px 24px;background:#f8fafc;color:#64748b;font-size:12px;">
                                %s support: %s
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
                """.formatted(
                escapeHtml(companyName),
                safeLogo,
                escapeHtml(heading),
                escapeHtml(intro),
                escapeHtml(buttonUrl),
                escapeHtml(buttonText),
                fallback,
                escapeHtml(footNote),
                escapeHtml(companyName),
                safeSupport
        );
    }

    private String normalizedBaseUrl(String raw, String propertyName) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(propertyName + " is not configured");
        }
        String trimmed = raw.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw new IllegalStateException(propertyName + " must start with http:// or https://");
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private void warnIfLocalBase(String baseUrl, String propertyName) {
        if (baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1")) {
            log.warn("{} currently points to local address ({}). Set a public domain for production emails.", propertyName, baseUrl);
        }
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
