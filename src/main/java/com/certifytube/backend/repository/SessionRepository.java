package com.certifytube.backend.repository;

import com.certifytube.backend.model.Session;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, String> {

    /** Find the most recent open (un-ended) session for a user+video. */
    Optional<Session> findTopByUserIdAndVideoIdAndEndedAtUtcIsNullOrderByCreatedAtUtcDesc(
            String userId, String videoId);

    /**
     * All sessions for a user, most recent first (for dashboard - all statuses).
     */
    List<Session> findByUserIdOrderByCreatedAtUtcDesc(String userId);

    /** Sessions for a user filtered by status (for dashboard filtering). */
    List<Session> findByUserIdAndStatusInOrderByCreatedAtUtcDesc(String userId, Collection<String> statuses);
}
