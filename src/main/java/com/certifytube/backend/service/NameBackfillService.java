package com.certifytube.backend.service;

import com.certifytube.backend.model.Certificate;
import com.certifytube.backend.model.UserAccount;
import com.certifytube.backend.repository.CertificateRepository;
import com.certifytube.backend.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NameBackfillService {

    private final UserAccountRepository userAccountRepository;
    private final CertificateRepository certificateRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfillNames() {
        List<UserAccount> allUsers = userAccountRepository.findAll();
        List<UserAccount> userUpdates = new ArrayList<>();
        Map<Long, String> normalizedNameByUserId = new HashMap<>();

        for (UserAccount user : allUsers) {
            String normalizedName = normalizeName(user.getName(), user.getEmail(), user.getId());
            normalizedNameByUserId.put(user.getId(), normalizedName);

            String storedName = user.getName();
            if (storedName == null || !storedName.equals(normalizedName)) {
                user.setName(normalizedName);
                userUpdates.add(user);
            }
        }

        if (!userUpdates.isEmpty()) {
            userAccountRepository.saveAll(userUpdates);
        }

        List<Certificate> allCertificates = certificateRepository.findAll();
        List<Certificate> certificateUpdates = new ArrayList<>();

        for (Certificate cert : allCertificates) {
            String learnerName = cert.getLearnerName();
            if (!needsCertificateLearnerNameBackfill(learnerName)) {
                continue;
            }

            String normalizedName = normalizedNameByUserId.get(cert.getUserId());
            if (normalizedName == null || normalizedName.isBlank()) {
                normalizedName = "Learner";
            }

            cert.setLearnerName(normalizedName);
            certificateUpdates.add(cert);
        }

        if (!certificateUpdates.isEmpty()) {
            certificateRepository.saveAll(certificateUpdates);
        }

        log.info(
                "Name backfill completed: usersUpdated={}, certificatesUpdated={}",
                userUpdates.size(),
                certificateUpdates.size()
        );
    }

    private String normalizeName(String name, String email, Long userId) {
        if (name != null) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }

        String fallback = fallbackNameFromEmail(email);
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }

        return userId == null ? "Learner" : "Learner " + userId;
    }

    private String fallbackNameFromEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String trimmedEmail = email.trim();
        int atIndex = trimmedEmail.indexOf('@');
        String prefix = atIndex > 0 ? trimmedEmail.substring(0, atIndex) : trimmedEmail;
        prefix = prefix.trim();
        return prefix.isEmpty() ? null : prefix;
    }

    private boolean needsCertificateLearnerNameBackfill(String learnerName) {
        if (learnerName == null) {
            return true;
        }
        String trimmed = learnerName.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        return trimmed.contains("@");
    }
}
