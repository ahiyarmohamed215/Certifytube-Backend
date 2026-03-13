package com.certifytube.backend.service;

import com.certifytube.backend.model.Quiz;
import com.certifytube.backend.model.Session;
import com.certifytube.backend.repository.CertificateRepository;
import com.certifytube.backend.repository.EngagementResultRepository;
import com.certifytube.backend.repository.QuizAttemptRepository;
import com.certifytube.backend.repository.QuizQuestionRepository;
import com.certifytube.backend.repository.QuizRepository;
import com.certifytube.backend.repository.SessionEventRepository;
import com.certifytube.backend.repository.SessionFeaturesRepository;
import com.certifytube.backend.repository.SessionRepository;
import com.certifytube.backend.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private final UserAccountRepository userAccountRepository;
    private final CertificateRepository certificateRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final SessionRepository sessionRepository;
    private final SessionEventRepository sessionEventRepository;
    private final SessionFeaturesRepository sessionFeaturesRepository;
    private final EngagementResultRepository engagementResultRepository;

    @Transactional
    public void deleteUserAndOwnedData(Long userId) {
        if (userId == null || !userAccountRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found");
        }

        certificateRepository.deleteByUserId(userId);

        List<Quiz> quizzes = quizRepository.findByUserId(userId);
        if (!quizzes.isEmpty()) {
            quizAttemptRepository.deleteByQuizIn(quizzes);
            quizQuestionRepository.deleteByQuizIn(quizzes);
            quizRepository.deleteAll(quizzes);
        }

        String userIdText = String.valueOf(userId);
        List<Session> sessions = sessionRepository.findByUserIdOrderByCreatedAtUtcDesc(userIdText);
        for (Session session : sessions) {
            String sessionId = session.getSessionId();
            sessionEventRepository.deleteBySessionId(sessionId);
            sessionFeaturesRepository.deleteBySessionId(sessionId);
            engagementResultRepository.deleteBySessionId(sessionId);
        }
        if (!sessions.isEmpty()) {
            sessionRepository.deleteAll(sessions);
        }

        userAccountRepository.deleteById(userId);
        log.info("Deleted account and owned data for userId={}", userId);
    }
}
