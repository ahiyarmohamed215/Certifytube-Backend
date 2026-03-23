package com.certifytube.backend.service;

import com.certifytube.backend.service.email.TransactionalEmailMessage;
import com.certifytube.backend.service.email.TransactionalEmailProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailDeliveryService {

    private final TransactionalEmailProvider transactionalEmailProvider;

    @Value("${app.email.sender:${app.mail.from:${spring.mail.username:}}}")
    private String senderEmail;

    @Value("${app.frontend-base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @Value("${app.company-name:CertifyTube}")
    private String companyName;

    @Value("${app.support-email:${app.email.sender:${app.mail.from:${spring.mail.username:}}}}")
    private String supportEmail;

    @Value("${app.mail.reply-to:}")
    private String replyTo;

    @Value("${app.mail.logo-url:}")
    private String logoUrl;

    @Async("mailTaskExecutor")
    public void sendEmailVerification(String toEmail, String rawToken) {
        long startedAt = System.nanoTime();
        log.info("MAIL_VERIFY_START to={}", maskEmail(toEmail));
        String frontendBase = normalizedBaseUrl(frontendBaseUrl, "app.frontend-base-url");
        warnIfLocalBase(frontendBase, "app.frontend-base-url");

        String encodedToken = URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        String verifyLink = frontendBase + "/verify-email?token=" + encodedToken;
        String subject = "Verify your " + companyName + " account";

        String plainText = """
                Welcome to %s.

                Please verify your email:
                %s

                If the button does not open, copy and paste this link in your browser:
                %s

                If you did not create this account, ignore this email.
                """.formatted(companyName, verifyLink, verifyLink);

        String html = buildHtmlMail(
                "Verify Your Email",
                "Welcome to " + companyName,
                "Please confirm your email address to activate your account and secure your access.",
                "Verify Email",
                verifyLink,
                "If the button does not work, use this link:",
                verifyLink,
                "If you did not create this account, you can safely ignore this email.");

        try {
            sendMail(toEmail, subject, plainText, html);
            log.info("MAIL_VERIFY_DONE to={} durationMs={}", maskEmail(toEmail), elapsedMs(startedAt));
        } catch (Exception ex) {
            log.error("MAIL_VERIFY_FAILED to={} durationMs={} reason={}",
                    maskEmail(toEmail),
                    elapsedMs(startedAt),
                    ex.getMessage(),
                    ex);
        }
    }

    @Async("mailTaskExecutor")
    public void sendPasswordReset(String toEmail, String rawToken) {
        long startedAt = System.nanoTime();
        log.info("MAIL_RESET_START to={}", maskEmail(toEmail));
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
                "If the button does not work, use this link:",
                resetLink,
                "This link expires soon. If you did not request this, ignore this email.");

        try {
            sendMail(toEmail, subject, plainText, html);
            log.info("MAIL_RESET_DONE to={} durationMs={}", maskEmail(toEmail), elapsedMs(startedAt));
        } catch (Exception ex) {
            log.error("MAIL_RESET_FAILED to={} durationMs={} reason={}",
                    maskEmail(toEmail),
                    elapsedMs(startedAt),
                    ex.getMessage(),
                    ex);
        }
    }

    private void sendMail(String toEmail, String subject, String plainText, String htmlBody) {
        transactionalEmailProvider.send(new TransactionalEmailMessage(
                toEmail,
                subject,
                plainText,
                htmlBody,
                replyTo
        ));
    }

    private String buildHtmlMail(
            String title,
            String heading,
            String intro,
            String buttonText,
            String buttonUrl,
            String fallbackLabel,
            String fallbackUrl,
            String footNote) {

        String fallback = "";
        if (fallbackLabel != null && fallbackUrl != null) {
            fallback = "<table role='presentation' width='100%' cellspacing='0' cellpadding='0' " +
                    "style='margin:20px 0 0;background:#fff7ed;border:1px solid #fed7aa;border-radius:10px;'>" +
                    "<tr><td style='padding:14px 16px;'>" +
                    "<p style='margin:0 0 8px;color:#9a3412;font-size:13px;font-weight:600;'>" + escapeHtml(fallbackLabel) + "</p>" +
                    "<a href='" + escapeHtml(fallbackUrl) + "' " +
                    "style='color:#b91c1c;font-size:12px;line-height:1.6;word-break:break-all;text-decoration:none;'>" +
                    escapeHtml(fallbackUrl) +
                    "</a></td></tr></table>";
        }

        String safeLogo = (logoUrl == null || logoUrl.isBlank()) ? "" :
                "<img src='" + escapeHtml(logoUrl) + "' alt='" + escapeHtml(companyName) +
                        "' style='height:34px;display:block;margin:0 auto 16px;' />";

        String effectiveSupportEmail = (supportEmail == null || supportEmail.isBlank()) ? senderEmail : supportEmail;
        String safeSupport = (effectiveSupportEmail == null || effectiveSupportEmail.isBlank()) ? "" : escapeHtml(effectiveSupportEmail);

        return """
                <!doctype html>
                <html>
                  <head>
                    <meta charset="UTF-8" />
                    <meta name="viewport" content="width=device-width,initial-scale=1" />
                    <title>%s</title>
                  </head>
                  <body style="margin:0;padding:0;background:#0f1115;font-family:'Segoe UI',Arial,sans-serif;">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="padding:28px 10px;background:radial-gradient(circle at 15%% 15%%,#1f2937 0,#0f1115 55%%,#090b0f 100%%);">
                      <tr>
                        <td align="center">
                          <table role="presentation" width="640" cellspacing="0" cellpadding="0" style="max-width:640px;width:100%%;">
                            <tr>
                              <td style="padding:0;">
                                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:linear-gradient(135deg,#f59e0b 0%%,#ef4444 36%%,#7f1d1d 100%%);border-radius:18px 18px 0 0;">
                                  <tr>
                                    <td style="padding:20px 24px 18px;color:#ffffff;">
                                      <p style="margin:0;font-size:11px;letter-spacing:1.1px;text-transform:uppercase;opacity:0.92;">Secure Account Message</p>
                                      <p style="margin:6px 0 0;font-size:18px;font-weight:700;">%s</p>
                                    </td>
                                  </tr>
                                </table>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:28px 24px;background:#ffffff;border:1px solid #e2e8f0;border-top:0;border-radius:0 0 18px 18px;">
                                %s
                                <h1 style="margin:0 0 10px;font-size:29px;line-height:1.25;color:#0f172a;font-weight:700;">%s</h1>
                                <p style="margin:0 0 22px;color:#334155;line-height:1.7;font-size:15px;">%s</p>
                                <table role="presentation" cellspacing="0" cellpadding="0" style="margin:0 0 10px;">
                                  <tr>
                                    <td style="border-radius:999px;background:linear-gradient(135deg,#ef4444,#991b1b);">
                                      <a href="%s" style="color:#ffffff;text-decoration:none;padding:13px 26px;border-radius:999px;display:inline-block;font-weight:700;font-size:14px;letter-spacing:.2px;">
                                        %s
                                      </a>
                                    </td>
                                  </tr>
                                </table>
                                <p style="margin:0;color:#64748b;font-size:12px;line-height:1.6;">
                                  For your security, this link is intended only for your account action.
                                </p>
                                %s
                                <p style="margin:18px 0 0;color:#475569;font-size:12px;line-height:1.7;">%s</p>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:16px 22px;color:#94a3b8;font-size:11px;text-align:center;">
                                %s support: <a href="mailto:%s" style="color:#cbd5e1;text-decoration:none;">%s</a>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
                """.formatted(
                escapeHtml(title),
                escapeHtml(companyName),
                safeLogo,
                escapeHtml(heading),
                escapeHtml(intro),
                escapeHtml(buttonUrl),
                escapeHtml(buttonText),
                fallback,
                escapeHtml(footNote),
                escapeHtml(companyName),
                safeSupport,
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
