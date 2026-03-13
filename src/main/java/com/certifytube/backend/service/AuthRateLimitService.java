package com.certifytube.backend.service;

import com.certifytube.backend.exception.TooManyRequestsException;
import com.certifytube.backend.model.AuthRateLimitEvent;
import com.certifytube.backend.repository.AuthRateLimitEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class AuthRateLimitService {

    private static final String SUBJECT_EMAIL = "EMAIL";
    private static final String SUBJECT_IP = "IP";

    private final AuthRateLimitEventRepository authRateLimitEventRepository;

    @Value("${auth.rate-limit.window-minutes:60}")
    private long windowMinutes;

    @Value("${auth.rate-limit.email.max-requests:5}")
    private long emailMaxRequests;

    @Value("${auth.rate-limit.ip.max-requests:30}")
    private long ipMaxRequests;

    @Value("${auth.rate-limit.cooldown-seconds:30}")
    private long cooldownSeconds;

    @Transactional
    public void enforceAndRecord(String action, String email, String clientIp) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(windowMinutes, ChronoUnit.MINUTES);

        String emailHash = null;
        if (email != null && !email.isBlank()) {
            String normalizedEmail = email.trim().toLowerCase();
            emailHash = sha256Hex(normalizedEmail);
            enforceSubjectLimit(action, emailHash, emailMaxRequests, cutoff, now);
        }

        String ipHash = null;
        if (clientIp != null && !clientIp.isBlank()) {
            ipHash = sha256Hex(clientIp.trim());
            enforceSubjectLimit(action, ipHash, ipMaxRequests, cutoff, now);
        }

        if (emailHash != null) {
            record(action, SUBJECT_EMAIL, emailHash, now);
        }
        if (ipHash != null) {
            record(action, SUBJECT_IP, ipHash, now);
        }
    }

    private void enforceSubjectLimit(String action, String subjectHash, long maxRequests, Instant cutoff, Instant now) {
        if (maxRequests <= 0) {
            return;
        }

        long count = authRateLimitEventRepository.countByActionAndSubjectHashAndCreatedAtUtcAfter(
                action, subjectHash, cutoff);
        if (count >= maxRequests) {
            throw new TooManyRequestsException("Too many requests. Please try again later.");
        }

        if (cooldownSeconds > 0) {
            authRateLimitEventRepository.findTopByActionAndSubjectHashOrderByCreatedAtUtcDesc(action, subjectHash)
                    .ifPresent(last -> {
                        if (last.getCreatedAtUtc() != null
                                && last.getCreatedAtUtc().plusSeconds(cooldownSeconds).isAfter(now)) {
                            throw new TooManyRequestsException("Please wait before trying again.");
                        }
                    });
        }
    }

    private void record(String action, String subjectType, String subjectHash, Instant now) {
        authRateLimitEventRepository.save(AuthRateLimitEvent.builder()
                .action(action)
                .subjectType(subjectType)
                .subjectHash(subjectHash)
                .createdAtUtc(now)
                .build());
    }

    private String sha256Hex(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash rate-limit subject", e);
        }
    }
}
