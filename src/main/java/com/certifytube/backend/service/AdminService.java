package com.certifytube.backend.service;

import com.certifytube.backend.dto.AdminEngagementResponse;
import com.certifytube.backend.dto.AdminLearnerProfileResponse;
import com.certifytube.backend.dto.AdminUserSummaryDto;
import com.certifytube.backend.model.Certificate;
import com.certifytube.backend.model.EngagementResult;
import com.certifytube.backend.model.Quiz;
import com.certifytube.backend.model.QuizQuestion;
import com.certifytube.backend.model.Role;
import com.certifytube.backend.model.Session;
import com.certifytube.backend.model.UserAccount;
import com.certifytube.backend.repository.CertificateRepository;
import com.certifytube.backend.repository.EngagementResultRepository;
import com.certifytube.backend.repository.QuizAttemptRepository;
import com.certifytube.backend.repository.QuizQuestionRepository;
import com.certifytube.backend.repository.QuizRepository;
import com.certifytube.backend.repository.SessionFeaturesRepository;
import com.certifytube.backend.repository.SessionRepository;
import com.certifytube.backend.repository.UserAccountRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<AdminUserSummaryDto> getLearners() {
        return userAccountRepository.findByRoleOrderByCreatedAtUtcDesc(Role.LEARNER)
                .stream()
                .map(this::toUserSummary)
                .toList();
    }

    public AdminLearnerProfileResponse getLearnerProfile(Long learnerId) {
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
                .build();
    }

    @Transactional
    public void deleteCertificate(String certificateId) {
        if (!certificateRepository.existsById(certificateId)) {
            throw new IllegalArgumentException("Certificate not found with id: " + certificateId);
        }
        certificateRepository.deleteById(certificateId);
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

