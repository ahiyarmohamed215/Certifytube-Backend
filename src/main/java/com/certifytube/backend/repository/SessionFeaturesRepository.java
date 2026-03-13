package com.certifytube.backend.repository;

import com.certifytube.backend.model.SessionFeatures;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SessionFeaturesRepository extends JpaRepository<SessionFeatures, Long> {
    Optional<SessionFeatures> findBySessionId(String sessionId);
    Optional<SessionFeatures> findTopBySessionIdOrderByCreatedAtUtcDesc(String sessionId);
    void deleteBySessionId(String sessionId);
}
