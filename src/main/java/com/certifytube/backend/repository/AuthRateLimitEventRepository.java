package com.certifytube.backend.repository;

import com.certifytube.backend.model.AuthRateLimitEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface AuthRateLimitEventRepository extends JpaRepository<AuthRateLimitEvent, Long> {
    long countByActionAndSubjectHashAndCreatedAtUtcAfter(String action, String subjectHash, Instant after);

    Optional<AuthRateLimitEvent> findTopByActionAndSubjectHashOrderByCreatedAtUtcDesc(String action, String subjectHash);

    void deleteByCreatedAtUtcBefore(Instant cutoff);
}
