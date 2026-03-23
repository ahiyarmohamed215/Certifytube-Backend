package com.certifytube.backend.service.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "brevo", matchIfMissing = true)
public class BrevoTransactionalEmailProvider implements TransactionalEmailProvider {

    @Value("${app.email.brevo.base-url:https://api.brevo.com}")
    private String brevoBaseUrl;

    @Value("${app.email.brevo.api-key:}")
    private String brevoApiKey;

    @Value("${app.email.sender:${app.mail.from:${spring.mail.username:}}}")
    private String senderEmail;

    @Value("${app.company-name:CertifyTube}")
    private String companyName;

    @Override
    public void send(TransactionalEmailMessage message) {
        long startedAt = System.nanoTime();
        String toMasked = maskEmail(message.toEmail());
        log.debug("mail.provider.brevo.send.start to={}", toMasked);

        String apiKey = trimToEmpty(brevoApiKey);
        String sender = trimToEmpty(senderEmail);
        if (apiKey.isBlank()) {
            throw new IllegalStateException("Brevo API key is missing (set BREVO_API_KEY / app.email.brevo.api-key)");
        }
        if (sender.isBlank()) {
            throw new IllegalStateException("Sender email is missing (set APP_EMAIL_SENDER / app.email.sender)");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("sender", buildSender(sender));
        payload.put("to", List.of(Map.of("email", message.toEmail())));
        payload.put("subject", message.subject());
        payload.put("textContent", message.plainTextBody());
        payload.put("htmlContent", message.htmlBody());

        String replyTo = trimToEmpty(message.replyTo());
        if (!replyTo.isBlank()) {
            payload.put("replyTo", Map.of("email", replyTo));
        }

        try {
            String responseBody = client()
                    .post()
                    .uri("/v3/smtp/email")
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(body -> Mono.error(new IllegalStateException(
                                    "Brevo API error status=" + response.statusCode().value() + " body=" + truncate(body, 500)
                            ))))
                    .bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .block();

            log.debug("mail.provider.brevo.send.success to={} durationMs={} responseBytes={}",
                    toMasked,
                    elapsedMs(startedAt),
                    responseBody == null ? 0 : responseBody.length());
        } catch (Exception ex) {
            log.error("mail.provider.brevo.send.failed to={} durationMs={} reason={}",
                    toMasked,
                    elapsedMs(startedAt),
                    ex.getMessage(),
                    ex);
            throw new IllegalStateException("Unable to send email via Brevo API", ex);
        }
    }

    private Map<String, Object> buildSender(String email) {
        Map<String, Object> sender = new HashMap<>();
        sender.put("email", email);
        String safeCompanyName = trimToEmpty(companyName);
        if (!safeCompanyName.isBlank()) {
            sender.put("name", safeCompanyName);
        }
        return sender;
    }

    private WebClient client() {
        return WebClient.builder()
                .baseUrl(normalizedBaseUrl(brevoBaseUrl))
                .build();
    }

    private String normalizedBaseUrl(String rawUrl) {
        String value = trimToEmpty(rawUrl);
        if (value.isBlank()) {
            throw new IllegalStateException("app.email.brevo.base-url is required");
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String truncate(String value, int max) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
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
