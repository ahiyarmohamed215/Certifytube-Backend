package com.certifytube.backend.service;

import com.certifytube.backend.dto.AdminEngagementResponse;
import com.certifytube.backend.dto.AdminLearnerProfileResponse;
import com.certifytube.backend.dto.AdminUserSummaryDto;
import com.certifytube.backend.dto.AdminUserUpdateRequest;
import com.certifytube.backend.model.Certificate;
import com.certifytube.backend.model.EngagementResult;
import com.certifytube.backend.model.Quiz;
import com.certifytube.backend.model.QuizQuestion;
import com.certifytube.backend.model.Role;
import com.certifytube.backend.model.Session;
import com.certifytube.backend.model.UserAccount;
import com.certifytube.backend.model.YouTubeSearchCache;
import com.certifytube.backend.model.YouTubeSearchCacheItem;
import com.certifytube.backend.repository.CertificateRepository;
import com.certifytube.backend.repository.EngagementResultRepository;
import com.certifytube.backend.repository.QuizAttemptRepository;
import com.certifytube.backend.repository.QuizQuestionRepository;
import com.certifytube.backend.repository.QuizRepository;
import com.certifytube.backend.repository.SessionFeaturesRepository;
import com.certifytube.backend.repository.SessionRepository;
import com.certifytube.backend.repository.UserAccountRepository;
import com.certifytube.backend.repository.YouTubeSearchCacheItemRepository;
import com.certifytube.backend.repository.YouTubeSearchCacheRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserAccountRepository userAccountRepository;
    private final SessionRepository sessionRepository;
    private final SessionFeaturesRepository sessionFeaturesRepository;
    private final CertificateRepository certificateRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final EngagementResultRepository engagementResultRepository;
    private final YouTubeSearchCacheRepository youTubeSearchCacheRepository;
    private final YouTubeSearchCacheItemRepository youTubeSearchCacheItemRepository;
    private final AccountDeletionService accountDeletionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Users

    public List<AdminUserSummaryDto> getAllUsers() {
        List<UserAccount> users = userAccountRepository.findAll();
        users.sort(Comparator.comparing(UserAccount::getCreatedAtUtc, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return users.stream().map(this::toUserSummary).toList();
    }

    public List<AdminUserSummaryDto> getLearners() {
        return userAccountRepository.findByRoleOrderByCreatedAtUtcDesc(Role.LEARNER)
                .stream()
                .map(this::toUserSummary)
                .toList();
    }

    public AdminUserSummaryDto getUserById(Long id) {
        return toUserSummary(requireUser(id));
    }

    @Transactional
    public AdminUserSummaryDto updateUserRole(Long id, Role role) {
        UserAccount user = requireUser(id);
        user.setRole(role);
        return toUserSummary(userAccountRepository.save(user));
    }

    @Transactional
    public AdminUserSummaryDto updateUser(Long id, AdminUserUpdateRequest req) {
        UserAccount user = requireUser(id);

        if (req.getEmail() != null) {
            String normalized = normalizeEmail(req.getEmail());
            if (userAccountRepository.existsByEmailAndIdNot(normalized, id)) {
                throw new IllegalArgumentException("Email already in use");
            }
            user.setEmail(normalized);
        }

        if (req.getName() != null) {
            String name = req.getName().trim();
            if (name.length() < 2 || name.length() > 255) {
                throw new IllegalArgumentException("Name must be between 2 and 255 characters");
            }
            user.setName(name);
        }

        if (req.getRole() != null && !req.getRole().isBlank()) {
            try {
                user.setRole(Role.valueOf(req.getRole().trim().toUpperCase()));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid role: " + req.getRole() + ". Valid roles: ADMIN, LEARNER");
            }
        }

        if (req.getActive() != null) {
            user.setActive(req.getActive());
        }

        if (req.getEmailVerified() != null) {
            user.setEmailVerified(req.getEmailVerified());
            user.setEmailVerifiedAtUtc(Boolean.TRUE.equals(req.getEmailVerified()) ? LocalDateTime.now() : null);
        }

        return toUserSummary(userAccountRepository.save(user));
    }

    @Transactional
    public AdminUserSummaryDto setUserActive(Long id, boolean active) {
        UserAccount user = requireUser(id);
        user.setActive(active);
        return toUserSummary(userAccountRepository.save(user));
    }

    @Transactional
    public void deleteUser(Long id) {
        accountDeletionService.deleteUserAndOwnedData(id);
    }

    // Learner profile

    public AdminLearnerProfileResponse getLearnerProfile(Long learnerId, int searchLimit) {
        UserAccount learner = requireUser(learnerId);
        if (learner.getRole() != Role.LEARNER) {
            throw new IllegalArgumentException("Selected user is not a learner");
        }

        List<Session> sessions = sessionRepository.findByUserIdOrderByCreatedAtUtcDesc(String.valueOf(learnerId));
        List<Quiz> quizzes = quizRepository.findByUserIdOrderByCreatedAtUtcDesc(learnerId);
        List<Certificate> certificates = certificateRepository.findByUserIdOrderByCreatedAtUtcDesc(learnerId);

        return AdminLearnerProfileResponse.builder()
                .learner(toUserSummary(learner))
                .sessions(sessions.stream().map(this::toSessionInsight).toList())
                .quizzes(quizzes.stream().map(this::toQuizInsight).toList())
                .certificates(certificates.stream().map(this::toCertificateInsight).toList())
                .youtubeSearches(getYouTubeSearches(searchLimit))
                .build();
    }

    public List<AdminLearnerProfileResponse.YouTubeSearchInsight> getYouTubeSearches(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<YouTubeSearchCache> caches = youTubeSearchCacheRepository.findAll(Sort.by(Sort.Direction.DESC, "updatedAtUtc"));
        if (caches.isEmpty()) {
            return List.of();
        }

        List<AdminLearnerProfileResponse.YouTubeSearchInsight> out = new ArrayList<>();
        for (YouTubeSearchCache cache : caches.stream().limit(safeLimit).toList()) {
            List<YouTubeSearchCacheItem> items = youTubeSearchCacheItemRepository.findByCacheOrderByPositionIndexAsc(cache);
            List<AdminLearnerProfileResponse.YouTubeSearchItemInsight> mappedItems = items.stream()
                    .map(item -> AdminLearnerProfileResponse.YouTubeSearchItemInsight.builder()
                            .positionIndex(item.getPositionIndex())
                            .videoId(item.getVideo().getVideoId())
                            .title(item.getVideo().getTitle())
                            .channelTitle(item.getVideo().getChannelTitle())
                            .thumbnailUrl(item.getVideo().getThumbnailUrl())
                            .publishedAt(item.getVideo().getPublishedAt())
                            .iframeUrl(item.getVideo().getIframeUrl())
                            .categoryId(item.getVideo().getCategoryId())
                            .build())
                    .toList();

            out.add(AdminLearnerProfileResponse.YouTubeSearchInsight.builder()
                    .cacheId(cache.getId())
                    .queryText(cache.getQueryText())
                    .lastRefreshedOn(cache.getLastRefreshedOn())
                    .createdAtUtc(cache.getCreatedAtUtc())
                    .updatedAtUtc(cache.getUpdatedAtUtc())
                    .items(mappedItems)
                    .build());
        }

        return out;
    }

    // Sessions

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

    // Certificates

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

    // Quizzes

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

    // Stats

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", userAccountRepository.count());
        stats.put("totalSessions", sessionRepository.count());
        stats.put("totalCertificates", certificateRepository.count());
        stats.put("totalQuizzes", quizRepository.count());
        return stats;
    }

    // Engagement results

    public AdminEngagementResponse getEngagementResult(String sessionId) {
        EngagementResult result = engagementResultRepository
                .findTopBySessionIdOrderByCreatedAtUtcDesc(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No engagement result found for session: " + sessionId));
        return toEngagementResponse(result);
    }

    private AdminUserSummaryDto toUserSummary(UserAccount user) {
        long sessionCount = sessionRepository.countByUserId(String.valueOf(user.getId()));
        long certificateCount = certificateRepository.countByUserId(user.getId());
        return AdminUserSummaryDto.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole() == null ? null : user.getRole().name())
                .active(!Boolean.FALSE.equals(user.getActive()))
                .emailVerified(user.getEmailVerified())
                .emailVerifiedAtUtc(user.getEmailVerifiedAtUtc())
                .createdAtUtc(user.getCreatedAtUtc())
                .sessionCount(sessionCount)
                .certificateCount(certificateCount)
                .build();
    }

    private AdminLearnerProfileResponse.SessionInsight toSessionInsight(Session session) {
        Map<String, Object> features = sessionFeaturesRepository.findTopBySessionIdOrderByCreatedAtUtcDesc(session.getSessionId())
                .map(f -> objectMapper.convertValue(f, new TypeReference<Map<String, Object>>() {}))
                .orElse(null);
        if (features != null) {
            features.remove("id");
        }

        AdminEngagementResponse engagement = engagementResultRepository.findTopBySessionIdOrderByCreatedAtUtcDesc(session.getSessionId())
                .map(this::toEngagementResponse)
                .orElse(null);

        return AdminLearnerProfileResponse.SessionInsight.builder()
                .sessionId(session.getSessionId())
                .userId(session.getUserId())
                .videoId(session.getVideoId())
                .videoTitle(session.getVideoTitle())
                .status(session.getStatus())
                .createdAtUtc(session.getCreatedAtUtc())
                .endedAtUtc(session.getEndedAtUtc())
                .lastPositionSec(session.getLastPositionSec())
                .videoDurationSec(session.getVideoDurationSec())
                .features(features)
                .engagement(engagement)
                .build();
    }

    private AdminLearnerProfileResponse.QuizInsight toQuizInsight(Quiz quiz) {
        List<QuizQuestion> questions = quizQuestionRepository.findByQuizOrderByPositionIndexAsc(quiz);
        List<AdminLearnerProfileResponse.QuizQuestionInsight> mappedQuestions = questions.stream()
                .map(q -> AdminLearnerProfileResponse.QuizQuestionInsight.builder()
                        .id(q.getId())
                        .questionUid(q.getQuestionUid())
                        .positionIndex(q.getPositionIndex())
                        .questionType(q.getQuestionType())
                        .questionText(q.getQuestionText())
                        .options(deserializeJson(q.getOptionsJson()))
                        .correctAnswer(q.getCorrectAnswer())
                        .explanationText(q.getExplanationText())
                        .build())
                .toList();

        AdminLearnerProfileResponse.QuizAttemptInsight latestAttempt = quizAttemptRepository
                .findTopByQuizAndUserIdOrderByCreatedAtUtcDesc(quiz, quiz.getUserId())
                .map(a -> AdminLearnerProfileResponse.QuizAttemptInsight.builder()
                        .attemptId(a.getId())
                        .correctCount(a.getCorrectCount())
                        .totalCount(a.getTotalCount())
                        .scorePercent(a.getScorePercent())
                        .passedFlag(a.getPassedFlag())
                        .answers(deserializeJson(a.getAnswersJson()))
                        .createdAtUtc(a.getCreatedAtUtc())
                        .build())
                .orElse(null);

        return AdminLearnerProfileResponse.QuizInsight.builder()
                .quizId(quiz.getQuizId())
                .sessionId(quiz.getSessionId())
                .videoId(quiz.getVideoId())
                .videoTitle(quiz.getVideoTitle())
                .difficulty(quiz.getDifficulty())
                .totalQuestions(quiz.getTotalQuestions())
                .createdAtUtc(quiz.getCreatedAtUtc())
                .latestAttempt(latestAttempt)
                .questions(mappedQuestions)
                .build();
    }

    private AdminLearnerProfileResponse.CertificateInsight toCertificateInsight(Certificate cert) {
        return AdminLearnerProfileResponse.CertificateInsight.builder()
                .certificateId(cert.getCertificateId())
                .certificateNumber(cert.getCertificateNumber())
                .sessionId(cert.getSessionId())
                .quizAttemptId(cert.getQuizAttemptId())
                .scorePercent(cert.getScorePercent())
                .finalEngagementScore(cert.getFinalEngagementScore())
                .finalQuizScore(cert.getFinalQuizScore())
                .learnerName(cert.getLearnerName())
                .videoTitle(cert.getVideoTitle())
                .videoId(cert.getVideoId())
                .status(cert.getStatus())
                .createdAtUtc(cert.getCreatedAtUtc())
                .build();
    }

    private AdminEngagementResponse toEngagementResponse(EngagementResult result) {
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

    private UserAccount requireUser(Long id) {
        return userAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));
    }

    private String normalizeEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        if (normalized.isBlank() || !normalized.contains("@")) {
            throw new IllegalArgumentException("Valid email is required");
        }
        return normalized;
    }

    private Object deserializeJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }
}

