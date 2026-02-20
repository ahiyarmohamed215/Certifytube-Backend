package com.certifytube.backend.service;

import com.certifytube.backend.exception.NotFoundException;
import com.certifytube.backend.model.Session;
import com.certifytube.backend.repository.SessionRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
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
        sessionRepository.save(session);
    }


    @Override
    public Session getById(String sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
    }
}
