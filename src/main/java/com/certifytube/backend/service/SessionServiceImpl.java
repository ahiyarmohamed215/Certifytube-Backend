package com.certifytube.backend.service;

import com.certifytube.backend.exception.NotFoundException;
import com.certifytube.backend.model.Session;
import com.certifytube.backend.repository.SessionRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SessionServiceImpl implements SessionService {

    private final SessionRepository sessionRepository;

    public SessionServiceImpl(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    @Transactional
    public Session startSession(String userId, String videoId, String videoTitle) {
        // Check for existing open session for this user+video → resume
        Optional<Session> existing = sessionRepository
                .findTopByUserIdAndVideoIdAndEndedAtUtcIsNullOrderByCreatedAtUtcDesc(userId, videoId);
        if (existing.isPresent()) {
            return existing.get();
        }

        // No open session → create a new one
        String sessionId = UUID.randomUUID().toString();
        Session s = new Session(sessionId, userId, videoId, videoTitle, Instant.now());
        return sessionRepository.save(s);
    }

    @Override
    @Transactional
    public void endSession(String sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        session.setEndedAtUtc(Instant.now());
        session.setStatus("COMPLETED");
        sessionRepository.save(session);
    }

    @Override
    public Session getById(String sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
    }

    @Override
    public List<Session> getAllByUserId(String userId) {
        return sessionRepository.findByUserIdOrderByCreatedAtUtcDesc(userId);
    }

    @Override
    @Transactional
    public void deleteSession(String sessionId, String userId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
        
        if (!session.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Session does not belong to authenticated user");
        }
        
        sessionRepository.delete(session);
    }
}
