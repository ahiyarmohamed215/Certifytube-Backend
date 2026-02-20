package com.certifytube.backend.service;

import com.certifytube.backend.client.MlServiceClient;
import com.certifytube.backend.dto.*;
import com.certifytube.backend.model.*;
import com.certifytube.backend.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class QuizService {

    private final SessionRepository sessionRepository;
    private final SessionEventRepository sessionEventRepository;
    private final EngagementResultRepository engagementResultRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final AuthenticatedUserService authenticatedUserService;
    private final CertificateService certificateService;
    private final MlServiceClient mlServiceClient;
    private final QuizGenerationService quizGenerationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${quiz.min-engagement-score:85}")
    private double minEngagementScore;

    @Value("${quiz.pass-score:80}")
    private double passScore;

    @Value("${quiz.max-failed-attempts:3}")
    private int maxFailedAttempts;

    @Value("${app.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    @Transactional(readOnly = true)
    public QuizEligibilityResponse eligibility(String sessionId) {
        UserAccount user = authenticatedUserService.currentUser();
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        if (!Objects.equals(session.getUserId(), String.valueOf(user.getId()))) {
            throw new AccessDeniedException("Session does not belong to authenticated user");
        }

        EngagementResult latest = engagementResultRepository.findTopBySessionIdOrderByCreatedAtUtcDesc(sessionId)
                .orElse(null);
        if (latest == null) {
            return QuizEligibilityResponse.builder()
                    .sessionId(sessionId)
                    .eligible(false)
                    .reason("Analyze session first")
                    .requiredEngagementScore(minEngagementScore)
                    .latestEngagementScore(null)
                    .engagementPassed(false)
                    .maxFailedAttempts(maxFailedAttempts)
                    .failedAttemptsUsed(0)
                    .remainingAttempts(maxFailedAttempts)
                    .build();
        }

        Double latestScore = latest.getEngagementScore();
        boolean engagementPassed = latestScore != null && latestScore >= minEngagementScore;
        if (!engagementPassed) {
            return QuizEligibilityResponse.builder()
                    .sessionId(sessionId)
                    .eligible(false)
                    .reason("Engagement score is below threshold")
                    .requiredEngagementScore(minEngagementScore)
                    .latestEngagementScore(latestScore)
                    .engagementPassed(false)
                    .maxFailedAttempts(maxFailedAttempts)
                    .failedAttemptsUsed(0)
                    .remainingAttempts(maxFailedAttempts)
                    .build();
        }

        Instant windowStart = latest.getCreatedAtUtc() == null ? Instant.EPOCH : latest.getCreatedAtUtc();
        int failedUsed = (int) quizAttemptRepository.countFailedAttemptsForSessionSince(
                user.getId(),
                sessionId,
                windowStart
        );
        int remaining = Math.max(maxFailedAttempts - failedUsed, 0);
        boolean eligible = failedUsed < maxFailedAttempts;
        String reason = eligible
                ? "Eligible"
                : "Maximum failed attempts reached. Rewatch and analyze again to unlock new attempts";

        return QuizEligibilityResponse.builder()
                .sessionId(sessionId)
                .eligible(eligible)
                .reason(reason)
                .requiredEngagementScore(minEngagementScore)
                .latestEngagementScore(latestScore)
                .engagementPassed(true)
                .maxFailedAttempts(maxFailedAttempts)
                .failedAttemptsUsed(failedUsed)
                .remainingAttempts(remaining)
                .build();
    }

    @Transactional
    public QuizResponse generate(QuizGenerateRequest req) {
        UserAccount user = authenticatedUserService.currentUser();
        Session session = sessionRepository.findById(req.getSessionId())
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        if (!Objects.equals(session.getUserId(), String.valueOf(user.getId()))) {
            throw new AccessDeniedException("Session does not belong to authenticated user");
        }

        EngagementResult latest = engagementResultRepository.findTopBySessionIdOrderByCreatedAtUtcDesc(req.getSessionId())
                .orElseThrow(() -> new IllegalStateException("Analyze session first"));

        if (latest.getEngagementScore() == null || latest.getEngagementScore() < minEngagementScore) {
            throw new IllegalStateException("Engagement score is below quiz eligibility threshold");
        }

        Instant engagementWindowStart = latest.getCreatedAtUtc() == null ? Instant.EPOCH : latest.getCreatedAtUtc();
        long failedAttemptsInWindow = quizAttemptRepository.countFailedAttemptsForSessionSince(
                user.getId(),
                session.getSessionId(),
                engagementWindowStart
        );
        if (failedAttemptsInWindow >= maxFailedAttempts) {
            throw new IllegalStateException(
                    "Maximum failed quiz attempts reached. Rewatch the video and analyze again (engagement >= "
                            + (int) minEngagementScore + ") to unlock new attempts."
            );
        }

        double videoDurationSec = sessionEventRepository.findBySessionIdOrderByCreatedAtUtcAsc(req.getSessionId()).stream()
                .map(SessionEvent::getVideoDurationSec)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);

        String difficulty = normalizeDifficulty(req.getDifficulty());
        String transcript = req.getTranscript() == null ? "" : req.getTranscript();
        Map<String, Object> ml = quizGenerationService.generateQuiz(session.getVideoId(), videoDurationSec, transcript, difficulty);

        List<QuestionDraft> drafts = extractQuestions(ml);
        if (drafts.isEmpty()) {
            throw new IllegalStateException("ML quiz response has no questions");
        }

        Quiz quiz = quizRepository.save(Quiz.builder()
                .quizId(UUID.randomUUID().toString())
                .sessionId(session.getSessionId())
                .userId(user.getId())
                .videoId(session.getVideoId())
                .videoTitle(session.getVideoTitle())
                .difficulty(difficulty)
                .totalQuestions(drafts.size())
                .createdAtUtc(Instant.now())
                .build());

        int pos = 1;
        for (QuestionDraft d : drafts) {
            quizQuestionRepository.save(QuizQuestion.builder()
                    .quiz(quiz)
                    .questionUid(UUID.randomUUID().toString())
                    .positionIndex(pos++)
                    .questionType(d.questionType())
                    .questionText(d.questionText())
                    .optionsJson(writeJson(d.options()))
                    .correctAnswer(d.correctAnswer())
                    .explanationText(d.explanation())
                    .build());
        }

        return getQuizForCurrentUser(quiz.getQuizId());
    }

    @Transactional(readOnly = true)
    public QuizResponse getQuizForCurrentUser(String quizId) {
        UserAccount user = authenticatedUserService.currentUser();
        Quiz quiz = quizRepository.findById(quizId).orElseThrow(() -> new IllegalArgumentException("Quiz not found"));
        if (!quiz.getUserId().equals(user.getId())) {
            throw new AccessDeniedException("Quiz does not belong to authenticated user");
        }

        List<QuizQuestionDto> questions = quizQuestionRepository.findByQuizOrderByPositionIndexAsc(quiz)
                .stream()
                .map(q -> QuizQuestionDto.builder()
                        .questionId(q.getQuestionUid())
                        .questionType(q.getQuestionType())
                        .questionText(q.getQuestionText())
                        .options(readOptions(q.getOptionsJson()))
                        .build())
                .toList();

        return QuizResponse.builder()
                .quizId(quiz.getQuizId())
                .sessionId(quiz.getSessionId())
                .videoId(quiz.getVideoId())
                .videoTitle(quiz.getVideoTitle())
                .difficulty(quiz.getDifficulty())
                .totalQuestions(quiz.getTotalQuestions())
                .questions(questions)
                .build();
    }

    @Transactional
    public QuizResultResponse submit(String quizId, QuizSubmitRequest req) {
        UserAccount user = authenticatedUserService.currentUser();
        Quiz quiz = quizRepository.findById(quizId).orElseThrow(() -> new IllegalArgumentException("Quiz not found"));
        if (!quiz.getUserId().equals(user.getId())) {
            throw new AccessDeniedException("Quiz does not belong to authenticated user");
        }

        if (quizAttemptRepository.findTopByQuizAndUserIdOrderByCreatedAtUtcDesc(quiz, user.getId()).isPresent()) {
            throw new IllegalStateException("Quiz already submitted");
        }

        List<QuizQuestion> questions = quizQuestionRepository.findByQuizOrderByPositionIndexAsc(quiz);
        int total = questions.size();
        int correct = 0;

        for (QuizQuestion q : questions) {
            String expected = normalizeAnswer(q.getCorrectAnswer());
            String provided = normalizeAnswer(req.getAnswers().get(q.getQuestionUid()));
            if (!expected.isBlank() && expected.equals(provided)) {
                correct++;
            }
        }

        double score = total == 0 ? 0.0 : (correct * 100.0) / total;
        boolean passed = score >= passScore;

        QuizAttempt attempt = quizAttemptRepository.save(QuizAttempt.builder()
                .quiz(quiz)
                .userId(user.getId())
                .answersJson(writeJson(req.getAnswers()))
                .correctCount(correct)
                .totalCount(total)
                .scorePercent(score)
                .passedFlag(passed)
                .createdAtUtc(Instant.now())
                .build());

        String certId = null;
        String verifyLink = null;
        if (passed) {
            Certificate cert = certificateService.issueIfAbsent(user.getId(), quiz.getSessionId(), attempt);
            certId = cert.getCertificateId();
            verifyLink = publicBaseUrl + "/api/certificates/verify/" + cert.getVerificationToken();
        }

        return QuizResultResponse.builder()
                .quizId(quizId)
                .correctCount(correct)
                .totalCount(total)
                .scorePercent(score)
                .passed(passed)
                .certificateId(certId)
                .verificationLink(verifyLink)
                .build();
    }

    @Transactional(readOnly = true)
    public QuizResultResponse latestResult(String quizId) {
        UserAccount user = authenticatedUserService.currentUser();
        Quiz quiz = quizRepository.findById(quizId).orElseThrow(() -> new IllegalArgumentException("Quiz not found"));
        if (!quiz.getUserId().equals(user.getId())) {
            throw new AccessDeniedException("Quiz does not belong to authenticated user");
        }

        QuizAttempt attempt = quizAttemptRepository.findTopByQuizAndUserIdOrderByCreatedAtUtcDesc(quiz, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Quiz not submitted"));

        Certificate cert = null;
        if (attempt.getPassedFlag()) {
            cert = certificateService.issueIfAbsent(user.getId(), quiz.getSessionId(), attempt);
        }

        return QuizResultResponse.builder()
                .quizId(quizId)
                .correctCount(attempt.getCorrectCount())
                .totalCount(attempt.getTotalCount())
                .scorePercent(attempt.getScorePercent())
                .passed(Boolean.TRUE.equals(attempt.getPassedFlag()))
                .certificateId(cert == null ? null : cert.getCertificateId())
                .verificationLink(cert == null ? null : (publicBaseUrl + "/api/certificates/verify/" + cert.getVerificationToken()))
                .build();
    }

    private String normalizeDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) return "medium";
        String d = difficulty.trim().toLowerCase();
        if (d.equals("easy") || d.equals("medium") || d.equals("hard")) return d;
        return "medium";
    }

    private List<QuestionDraft> extractQuestions(Map<String, Object> ml) {
        Object raw = ml.get("questions");
        if (!(raw instanceof List<?> list)) return List.of();

        List<QuestionDraft> out = new ArrayList<>();
        for (Object obj : list) {
            if (!(obj instanceof Map<?, ?> m)) continue;

            String qType = str(m.get("type"), "mcq");
            String qText = str(m.get("question"), "");
            List<String> options = toStringList(m.get("options"));
            String answer = str(m.get("answer"), "");
            String explanation = str(m.get("explanation"), "");

            if (qText.isBlank() || answer.isBlank()) continue;
            out.add(new QuestionDraft(qType, qText, options, answer, explanation));
        }
        return out;
    }

    private List<String> toStringList(Object o) {
        if (!(o instanceof List<?> list)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object v : list) {
            if (v != null && !v.toString().isBlank()) out.add(v.toString());
        }
        return out;
    }

    private String str(Object v, String def) {
        if (v == null) return def;
        String s = v.toString().trim();
        return s.isBlank() ? def : s;
    }

    private String normalizeAnswer(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize JSON", e);
        }
    }

    private List<String> readOptions(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private record QuestionDraft(
            String questionType,
            String questionText,
            List<String> options,
            String correctAnswer,
            String explanation
    ) {}
}
