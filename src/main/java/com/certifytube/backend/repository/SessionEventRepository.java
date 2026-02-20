package com.certifytube.backend.repository;

import com.certifytube.backend.model.SessionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SessionEventRepository extends JpaRepository<SessionEvent, String> {
    List<SessionEvent> findBySessionIdOrderByCreatedAtUtcAsc(String sessionId);
}
