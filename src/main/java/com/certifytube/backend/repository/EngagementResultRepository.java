package com.certifytube.backend.repository;

import com.certifytube.backend.model.EngagementResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EngagementResultRepository extends JpaRepository<EngagementResult, Long> {
    Optional<EngagementResult> findTopBySessionIdOrderByCreatedAtUtcDesc(String sessionId);
}
