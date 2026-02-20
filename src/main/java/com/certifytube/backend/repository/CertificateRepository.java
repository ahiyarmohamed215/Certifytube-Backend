package com.certifytube.backend.repository;

import com.certifytube.backend.model.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, String> {
    Optional<Certificate> findByVerificationToken(String verificationToken);
    Optional<Certificate> findTopByUserIdAndSessionIdOrderByCreatedAtUtcDesc(Long userId, String sessionId);
}
