package com.certifytube.backend.repository;

import com.certifytube.backend.model.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, String> {
    Optional<Certificate> findByVerificationToken(String verificationToken);
    Optional<Certificate> findTopByUserIdAndSessionIdOrderByCreatedAtUtcDesc(Long userId, String sessionId);
    List<Certificate> findByUserIdOrderByCreatedAtUtcDesc(Long userId);
    long countByUserId(Long userId);
    void deleteByUserId(Long userId);
}
