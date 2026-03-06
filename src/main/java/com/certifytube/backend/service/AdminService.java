package com.certifytube.backend.service;

import com.certifytube.backend.dto.AdminEngagementResponse;
import com.certifytube.backend.model.*;
import com.certifytube.backend.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserAccountRepository userAccountRepository;
    private final SessionRepository sessionRepository;
    private final CertificateRepository certificateRepository;
    private final QuizRepository quizRepository;
    private final EngagementResultRepository engagementResultRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ─── Users ────────────────────────────────────────

    public List<UserAccount> getAllUsers() {
        return userAccountRepository.findAll();
    }

    public UserAccount getUserById(Long id) {
        return userAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));
    }

    @Transactional
    public UserAccount updateUserRole(Long id, Role role) {
        UserAccount user = getUserById(id);
        user.setRole(role);
        return userAccountRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        if (!userAccountRepository.existsById(id)) {
            throw new IllegalArgumentException("User not found with id: " + id);
        }
        userAccountRepository.deleteById(id);
    }

    // ─── Sessions ─────────────────────────────────────

    public List<Session> getAllSessions() {
        return sessionRepository.findAll();
    }

    @Transactional
    public void deleteSession(String sessionId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new IllegalArgumentException("Session not found with id: " + sessionId);
        }
        sessionRepository.deleteById(sessionId);
    }

    // ─── Certificates ─────────────────────────────────

    public List<Certificate> getAllCertificates() {
        return certificateRepository.findAll();
    }

    @Transactional
    public void deleteCertificate(String certificateId) {
        if (!certificateRepository.existsById(certificateId)) {
            throw new IllegalArgumentException("Certificate not found with id: " + certificateId);
        }
        certificateRepository.deleteById(certificateId);
    }

    // ─── Quizzes ──────────────────────────────────────

    public List<Quiz> getAllQuizzes() {
        return quizRepository.findAll();
    }

    @Transactional
    public void deleteQuiz(String quizId) {
        if (!quizRepository.existsById(quizId)) {
            throw new IllegalArgumentException("Quiz not found with id: " + quizId);
        }
        quizRepository.deleteById(quizId);
    }

    // ─── Stats ────────────────────────────────────────

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", userAccountRepository.count());
        stats.put("totalSessions", sessionRepository.count());
        stats.put("totalCertificates", certificateRepository.count());
        stats.put("totalQuizzes", quizRepository.count());
        return stats;
    }

    // ─── Engagement Results (admin only) ──────────────

    public AdminEngagementResponse getEngagementResult(String sessionId) {
        EngagementResult result = engagementResultRepository
                .findTopBySessionIdOrderByCreatedAtUtcDesc(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No engagement result found for session: " + sessionId));

        Object topPositive = deserializeJson(result.getTopPositiveJson());
        Object topNegative = deserializeJson(result.getTopNegativeJson());

        return AdminEngagementResponse.builder()
                .sessionId(result.getSessionId())
                .model(result.getModelUsed())
                .engagementScore(result.getEngagementScore())
                .threshold(result.getThreshold())
                .status(result.getStatus())
                .explanation(result.getExplanation())
                .topPositive(topPositive)
                .topNegative(topNegative)
                .createdAtUtc(result.getCreatedAtUtc())
                .build();
    }

    private Object deserializeJson(String json) {
        if (json == null || json.isBlank())
            return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json; // fallback: return raw string
        }
    }
}
