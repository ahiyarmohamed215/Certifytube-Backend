package com.certifytube.backend.service;

import com.certifytube.backend.client.MlServiceClient;
import com.certifytube.backend.dto.QuizEligibilityResponse;
import com.certifytube.backend.dto.QuizGenerateRequest;
import com.certifytube.backend.dto.QuizQuestionDto;
import com.certifytube.backend.dto.QuizQuestionReviewDto;
import com.certifytube.backend.dto.QuizResponse;
import com.certifytube.backend.dto.QuizResultResponse;
import com.certifytube.backend.dto.QuizSubmitRequest;
import com.certifytube.backend.model.Certificate;
import com.certifytube.backend.model.EngagementResult;
import com.certifytube.backend.model.Quiz;
import com.certifytube.backend.model.QuizAttempt;
import com.certifytube.backend.model.QuizQuestion;
import com.certifytube.backend.model.Session;
import com.certifytube.backend.model.SessionEvent;
import com.certifytube.backend.model.UserAccount;
import com.certifytube.backend.model.YouTubeVideoCache;
import com.certifytube.backend.repository.EngagementResultRepository;
import com.certifytube.backend.repository.QuizAttemptRepository;
import com.certifytube.backend.repository.QuizQuestionRepository;
import com.certifytube.backend.repository.QuizRepository;
import com.certifytube.backend.repository.SessionEventRepository;
import com.certifytube.backend.repository.SessionRepository;
import com.certifytube.backend.repository.YouTubeVideoCacheRepository;
import com.certifytube.backend.util.StemCategoryUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
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
    private final YouTubeVideoCacheRepository videoCacheRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${quiz.min-engagement-score:0.85}")
    private double minEngagementScore;

    @Value("${quiz.pass-score:80}")
    private double passScore;

    @Value("${quiz.max-failed-attempts:2}")
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

        if (!checkStemEligible(session.getVideoId())) {
            return QuizEligibilityResponse.builder()
                    .sessionId(sessionId)
                    .eligible(false)
                    .reason("Only STEM-based skill videos are eligible for quiz and certification")
                    .requiredEngagementScore(minEngagementScore)
                    .latestEngagementScore(null)
                    .engagementPassed(false)
                    .maxFailedAttempts(maxFailedAttempts)
                    .failedAttemptsUsed(0)
                    .remainingAttempts(maxFailedAttempts)
                    .stemEligible(false)
                    .build();
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
                    .stemEligible(true)
                    .build();
        }

        Double latestScore = latest.getEngagementScore();
        if (latestScore == null || latestScore < minEngagementScore) {
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
                    .stemEligible(true)
                    .build();
        }

        LocalDateTime windowStart = latest.getCreatedAtUtc() == null ? LocalDateTime.MIN : latest.getCreatedAtUtc();
        int failedUsed = (int) quizAttemptRepository.countFailedAttemptsForSessionSince(
                user.getId(),
                sessionId,
                windowStart);
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
                .stemEligible(true)
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
        if (!checkStemEligible(session.getVideoId())) {
            throw new IllegalStateException("Only STEM-based skill videos are eligible for quiz and certification");
        }

        EngagementResult latest = engagementResultRepository
                .findTopBySessionIdOrderByCreatedAtUtcDesc(req.getSessionId())
                .orElseThrow(() -> new IllegalStateException("Analyze session first"));
        if (latest.getEngagementScore() == null || latest.getEngagementScore() < minEngagementScore) {
            throw new IllegalStateException("Engagement score is below quiz eligibility threshold");
        }

        LocalDateTime windowStart = latest.getCreatedAtUtc() == null ? LocalDateTime.MIN : latest.getCreatedAtUtc();
        long failedAttempts = quizAttemptRepository.countFailedAttemptsForSessionSince(
                user.getId(),
                session.getSessionId(),
                windowStart);
        if (failedAttempts >= maxFailedAttempts) {
            throw new IllegalStateException(
                    "Maximum failed quiz attempts reached. Rewatch the video and analyze again (engagement >= "
                            + formatEngagementThresholdForMessage() + ") to unlock new attempts.");
        }

        Optional<Quiz> recentQuiz = quizRepository.findTopBySessionIdAndUserIdOrderByCreatedAtUtcDesc(
                session.getSessionId(),
                user.getId());
        if (recentQuiz.isPresent()) {
            return getQuizForCurrentUser(recentQuiz.get().getQuizId());
        }

        double duration = resolveVideoDurationSec(req.getSessionId(), session);
        Map<String, Object> ml = mlServiceClient.generateQuiz(session.getSessionId(), session.getVideoId(), duration);

        String mlQuizId = str(ml.get("quiz_id"), null);
        if (mlQuizId == null || mlQuizId.isBlank()) {
            throw new IllegalStateException("ML quiz response has no quiz_id");
        }

        Optional<Quiz> existingByMlId = quizRepository.findById(mlQuizId);
        if (existingByMlId.isPresent()) {
            Quiz existing = existingByMlId.get();
            if (!existing.getUserId().equals(user.getId())) {
                throw new IllegalStateException("ML quiz_id is already linked to another user");
            }
            return getQuizForCurrentUser(existing.getQuizId());
        }

        List<QuestionDraft> drafts = extractQuestions(ml);
        if (drafts.isEmpty()) {
            throw new IllegalStateException("ML quiz response has no questions");
        }

        String difficulty = req.getDifficulty();
        if (difficulty == null || difficulty.isBlank()) {
            difficulty = drafts.stream().map(QuestionDraft::difficulty).filter(Objects::nonNull).findFirst().orElse("medium");
        }

        Quiz quiz = quizRepository.save(Quiz.builder()
                .quizId(mlQuizId)
                .sessionId(session.getSessionId())
                .userId(user.getId())
                .videoId(session.getVideoId())
                .videoTitle(session.getVideoTitle())
                .difficulty(difficulty)
                .totalQuestions(drafts.size())
                .createdAtUtc(LocalDateTime.now())
                .build());

        int pos = 1;
        for (QuestionDraft d : drafts) {
            quizQuestionRepository.save(QuizQuestion.builder()
                    .quiz(quiz)
                    .questionUid(d.questionId() != null ? d.questionId() : UUID.randomUUID().toString())
                    .positionIndex(pos++)
                    .questionType(d.questionType())
                    .questionText(d.questionText())
                    .optionsJson(writeJson(d.options()))
                    .correctAnswer("")
                    .explanationText(blankToNull(d.explanation()))
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

        QuizAttempt latestAttempt = quizAttemptRepository
                .findTopByQuizAndUserIdOrderByCreatedAtUtcDesc(quiz, user.getId())
                .orElse(null);
        if (latestAttempt != null && Boolean.TRUE.equals(latestAttempt.getPassedFlag())) {
            throw new IllegalStateException("Quiz already passed");
        }

        LocalDateTime windowStart = resolveAttemptWindowStart(quiz.getSessionId());
        long failedAttempts = quizAttemptRepository.countFailedAttemptsForSessionSince(
                user.getId(),
                quiz.getSessionId(),
                windowStart);
        if (failedAttempts >= maxFailedAttempts) {
            throw new IllegalStateException(
                    "Maximum failed quiz attempts reached. Rewatch the video and analyze again (engagement >= "
                            + formatEngagementThresholdForMessage() + ") to unlock new attempts.");
        }

        List<QuizQuestion> questions = quizQuestionRepository.findByQuizOrderByPositionIndexAsc(quiz);
        Map<String, String> providedAnswers = req.getAnswers() == null ? Map.of() : req.getAnswers();
        if (providedAnswers.isEmpty()) {
            throw new IllegalArgumentException("answers is required");
        }

        Map<String, String> normalized = normalizeAnswersByQuestionId(providedAnswers, questions);
        List<Map<String, Object>> mlAnswers = normalized.entrySet().stream().map(e -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("question_id", e.getKey());
            row.put("answer", e.getValue());
            return row;
        }).toList();

        Map<String, Object> ml = mlServiceClient.gradeQuiz(
                quiz.getQuizId(),
                quiz.getSessionId(),
                quiz.getVideoId(),
                mlAnswers);

        int total = asInt(ml.get("total_questions"), questions.size());
        int answered = asInt(ml.get("answered_questions"), normalized.size());
        int correct = asInt(ml.get("correct_answers"), 0);
        int incorrect = asInt(ml.get("incorrect_answers"), Math.max(answered - correct, 0));
        int unanswered = asInt(ml.get("unanswered_questions"), Math.max(total - answered, 0));
        double score = asDouble(ml.get("quiz_score_percent"));
        boolean passed = score >= passScore;
        List<QuizQuestionReviewDto> review = buildReviewFromMlResults(ml.get("results"), questions, normalized);

        QuizAttempt attempt = quizAttemptRepository.save(QuizAttempt.builder()
                .quiz(quiz)
                .userId(user.getId())
                .answersJson(writeJson(normalized))
                .correctCount(correct)
                .totalCount(total)
                .scorePercent(score)
                .passedFlag(passed)
                .reviewJson(writeJson(review))
                .mlResponseJson(writeJson(ml))
                .createdAtUtc(LocalDateTime.now())
                .build());

        String certId = null;
        String verifyLink = null;
        if (passed) {
            Certificate cert = certificateService.issueIfAbsent(user.getId(), quiz.getSessionId(), attempt);
            certId = cert.getCertificateId();
            verifyLink = publicBaseUrl + "/api/certificates/verify/" + cert.getVerificationToken();

            Session session = sessionRepository.findById(quiz.getSessionId()).orElse(null);
            if (session != null) {
                session.setStatus("CERTIFIED");
                sessionRepository.save(session);
            }
        }

        return QuizResultResponse.builder()
                .quizId(quizId)
                .correctCount(correct)
                .totalCount(total)
                .answeredQuestions(answered)
                .incorrectCount(incorrect)
                .unansweredQuestions(unanswered)
                .scorePercent(score)
                .passed(passed)
                .certificateId(certId)
                .verificationLink(verifyLink)
                .review(review)
                .build();
    }

    private LocalDateTime resolveAttemptWindowStart(String sessionId) {
        EngagementResult latest = engagementResultRepository.findTopBySessionIdOrderByCreatedAtUtcDesc(sessionId)
                .orElseThrow(() -> new IllegalStateException("Analyze session first"));
        if (latest.getEngagementScore() == null || latest.getEngagementScore() < minEngagementScore) {
            throw new IllegalStateException("Engagement score is below quiz eligibility threshold");
        }
        return latest.getCreatedAtUtc() == null ? LocalDateTime.MIN : latest.getCreatedAtUtc();
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

        List<QuizQuestion> questions = quizQuestionRepository.findByQuizOrderByPositionIndexAsc(quiz);
        List<QuizQuestionReviewDto> review = readReview(attempt.getReviewJson());
        if (review.isEmpty()) {
            review = buildFallbackReview(questions, readAnswers(attempt.getAnswersJson()));
        }
        int total = attempt.getTotalCount() == null ? 0 : attempt.getTotalCount();
        int correct = attempt.getCorrectCount() == null ? 0 : attempt.getCorrectCount();
        int answered = (int) review.stream().filter(r -> r.getSelectedAnswer() != null && !r.getSelectedAnswer().isBlank()).count();
        if (answered == 0) {
            answered = readAnswers(attempt.getAnswersJson()).size();
        }
        int unanswered = Math.max(total - answered, 0);
        int incorrect = Math.max(answered - correct, 0);

        Certificate cert = null;
        if (attempt.getPassedFlag()) {
            cert = certificateService.issueIfAbsent(user.getId(), quiz.getSessionId(), attempt);
        }

        return QuizResultResponse.builder()
                .quizId(quizId)
                .correctCount(correct)
                .totalCount(total)
                .answeredQuestions(answered)
                .incorrectCount(incorrect)
                .unansweredQuestions(unanswered)
                .scorePercent(attempt.getScorePercent())
                .passed(Boolean.TRUE.equals(attempt.getPassedFlag()))
                .certificateId(cert == null ? null : cert.getCertificateId())
                .verificationLink(cert == null ? null
                        : publicBaseUrl + "/api/certificates/verify/" + cert.getVerificationToken())
                .review(review)
                .build();
    }

    private boolean checkStemEligible(String videoId) {
        YouTubeVideoCache videoCache = videoCacheRepository.findByVideoId(videoId).orElse(null);
        return StemCategoryUtil.isStemVideo(videoCache);
    }

    private double resolveVideoDurationSec(String sessionId, Session session) {
        double fromEvents = sessionEventRepository.findBySessionIdOrderByCreatedAtUtcAsc(sessionId)
                .stream()
                .map(SessionEvent::getVideoDurationSec)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);
        if (fromEvents > 0) {
            return fromEvents;
        }
        return session.getVideoDurationSec() != null ? session.getVideoDurationSec() : 0.0;
    }

    private List<QuestionDraft> extractQuestions(Map<String, Object> ml) {
        Object raw = ml.get("questions");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<QuestionDraft> out = new ArrayList<>();
        for (Object obj : list) {
            if (!(obj instanceof Map<?, ?> row)) {
                continue;
            }
            String question = str(row.get("question"), "");
            if (question.isBlank()) {
                continue;
            }
            out.add(new QuestionDraft(
                    str(row.get("question_id"), null),
                    str(row.get("type"), "mcq"),
                    question,
                    toStringList(row.get("options")),
                    str(row.get("explanation"), ""),
                    str(row.get("difficulty"), "medium")));
        }
        return out;
    }

    private Map<String, String> normalizeAnswersByQuestionId(Map<String, String> provided, List<QuizQuestion> questions) {
        Map<String, String> byId = new LinkedHashMap<>();
        Map<Integer, String> byPosition = new LinkedHashMap<>();
        for (QuizQuestion q : questions) {
            byPosition.put(q.getPositionIndex(), q.getQuestionUid());
        }

        for (Map.Entry<String, String> entry : provided.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            String resolved = key.trim();
            String lowered = resolved.toLowerCase(Locale.ROOT);
            if (lowered.startsWith("q") && lowered.substring(1).matches("[0-9]+")) {
                String byPos = byPosition.get(Integer.parseInt(lowered.substring(1)));
                if (byPos != null) {
                    resolved = byPos;
                }
            } else if (lowered.matches("[0-9]+")) {
                String byPos = byPosition.get(Integer.parseInt(lowered));
                if (byPos != null) {
                    resolved = byPos;
                }
            }
            byId.put(resolved, entry.getValue());
        }
        return byId;
    }

    private List<QuizQuestionReviewDto> buildReviewFromMlResults(
            Object rawResults,
            List<QuizQuestion> questions,
            Map<String, String> normalizedAnswers) {

        Map<String, QuizQuestion> questionMap = new LinkedHashMap<>();
        for (QuizQuestion q : questions) {
            questionMap.put(q.getQuestionUid(), q);
        }

        if (!(rawResults instanceof List<?> list)) {
            return List.of();
        }

        List<QuizQuestionReviewDto> out = new ArrayList<>();
        for (Object obj : list) {
            if (!(obj instanceof Map<?, ?> row)) {
                continue;
            }
            String questionId = str(row.get("question_id"), null);
            if (questionId == null) {
                continue;
            }
            QuizQuestion q = questionMap.get(questionId);
            List<String> options = q == null ? List.of() : readOptions(q.getOptionsJson());
            out.add(QuizQuestionReviewDto.builder()
                    .questionId(questionId)
                    .questionType(q == null ? "mcq" : q.getQuestionType())
                    .questionText(q == null ? null : q.getQuestionText())
                    .options(options)
                    .selectedAnswer(str(row.get("submitted_answer"), normalizedAnswers.get(questionId)))
                    .correctAnswer(str(row.get("correct_answer"), null))
                    .correct(asBoolean(row.get("is_correct"), false))
                    .explanation(blankToNull(str(row.get("explanation"), null)))
                    .build());
        }
        return out;
    }

    private List<QuizQuestionReviewDto> buildFallbackReview(List<QuizQuestion> questions, Map<String, String> submittedAnswers) {
        List<QuizQuestionReviewDto> out = new ArrayList<>();
        for (QuizQuestion q : questions) {
            String submitted = submittedAnswers.get(q.getQuestionUid());
            if (submitted == null) {
                submitted = submittedAnswers.get("q" + q.getPositionIndex());
            }
            if (submitted == null) {
                submitted = submittedAnswers.get(String.valueOf(q.getPositionIndex()));
            }
            out.add(QuizQuestionReviewDto.builder()
                    .questionId(q.getQuestionUid())
                    .questionType(q.getQuestionType())
                    .questionText(q.getQuestionText())
                    .options(readOptions(q.getOptionsJson()))
                    .selectedAnswer(submitted)
                    .correctAnswer(null)
                    .correct(false)
                    .explanation(blankToNull(q.getExplanationText()))
                    .build());
        }
        return out;
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item != null && !item.toString().isBlank()) {
                out.add(item.toString());
            }
        }
        return out;
    }

    private String str(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.toString().trim();
        return text.isBlank() ? fallback : text;
    }

    private int asInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private double asDouble(Object value) {
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    private boolean asBoolean(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        String lowered = value.toString().trim().toLowerCase(Locale.ROOT);
        if ("true".equals(lowered)) {
            return true;
        }
        if ("false".equals(lowered)) {
            return false;
        }
        return fallback;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String formatEngagementThresholdForMessage() {
        if (minEngagementScore <= 1.0) {
            return String.format(Locale.US, "%.0f%%", minEngagementScore * 100.0);
        }
        return String.format(Locale.US, "%.0f", minEngagementScore);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize JSON", e);
        }
    }

    private List<String> readOptions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, String> readAnswers(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, String> parsed = objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
            return parsed == null ? Map.of() : parsed;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private List<QuizQuestionReviewDto> readReview(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<QuizQuestionReviewDto> parsed = objectMapper.readValue(
                    json, new TypeReference<List<QuizQuestionReviewDto>>() {});
            return parsed == null ? List.of() : parsed;
        } catch (Exception e) {
            return List.of();
        }
    }

    private record QuestionDraft(
            String questionId,
            String questionType,
            String questionText,
            List<String> options,
            String explanation,
            String difficulty) {
    }
}
