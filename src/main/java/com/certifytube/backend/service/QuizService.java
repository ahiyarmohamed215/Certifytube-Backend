package com.certifytube.backend.service;

import com.certifytube.backend.client.MlServiceClient;
import com.certifytube.backend.dto.*;
import com.certifytube.backend.model.*;
import com.certifytube.backend.repository.*;
import com.certifytube.backend.util.StemCategoryUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern LETTER_CHOICE_PATTERN = Pattern.compile("^\\(?([a-z])\\)?(?:[\\).:\\-].*)?$");
    private static final Pattern OPTION_PREFIX_PATTERN = Pattern.compile("^option\\s+([a-z])$");
    private static final Pattern NUMBER_CHOICE_PATTERN = Pattern.compile("^\\(?([1-9][0-9]*)\\)?(?:[\\).:\\-].*)?$");

    /**
     * Idempotency window: if quiz was generated within this many seconds, return
     * it.
     */
    private static final long IDEMPOTENCY_WINDOW_SEC = 60;

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

        // --- STEM gate ---
        boolean stemEligible = checkStemEligible(session.getVideoId());
        if (!stemEligible) {
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
                    .stemEligible(true)
                    .build();
        }

        Instant windowStart = latest.getCreatedAtUtc() == null ? Instant.EPOCH : latest.getCreatedAtUtc();
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

        // --- STEM gate ---
        if (!checkStemEligible(session.getVideoId())) {
            throw new IllegalStateException(
                    "Only STEM-based skill videos are eligible for quiz and certification");
        }

        EngagementResult latest = engagementResultRepository
                .findTopBySessionIdOrderByCreatedAtUtcDesc(req.getSessionId())
                .orElseThrow(() -> new IllegalStateException("Analyze session first"));

        if (latest.getEngagementScore() == null || latest.getEngagementScore() < minEngagementScore) {
            throw new IllegalStateException("Engagement score is below quiz eligibility threshold");
        }

        Instant engagementWindowStart = latest.getCreatedAtUtc() == null ? Instant.EPOCH : latest.getCreatedAtUtc();
        long failedAttemptsInWindow = quizAttemptRepository.countFailedAttemptsForSessionSince(
                user.getId(),
                session.getSessionId(),
                engagementWindowStart);
        if (failedAttemptsInWindow >= maxFailedAttempts) {
            throw new IllegalStateException(
                    "Maximum failed quiz attempts reached. Rewatch the video and analyze again (engagement >= "
                            + formatEngagementThresholdForMessage() + ") to unlock new attempts.");
        }

        // --- Idempotency: return existing quiz if recently generated ---
        Optional<Quiz> recentQuiz = quizRepository.findTopBySessionIdAndUserIdOrderByCreatedAtUtcDesc(
                session.getSessionId(), user.getId());
        if (recentQuiz.isPresent()) {
            Quiz existing = recentQuiz.get();
            if (existing.getCreatedAtUtc() != null
                    && Duration.between(existing.getCreatedAtUtc(), Instant.now())
                            .getSeconds() < IDEMPOTENCY_WINDOW_SEC) {
                return getQuizForCurrentUser(existing.getQuizId());
            }
        }

        // Get video duration from events
        double videoDurationSec = sessionEventRepository.findBySessionIdOrderByCreatedAtUtcAsc(req.getSessionId())
                .stream()
                .map(SessionEvent::getVideoDurationSec)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);

        // Call ML server for quiz generation
        Map<String, Object> ml = mlServiceClient.generateQuiz(
                session.getSessionId(),
                session.getVideoId(),
                videoDurationSec,
                session.getVideoTitle(),
                req.getNumQuestions(),
                req.getIncludeCoding());

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
                .difficulty(req.getDifficulty() != null ? req.getDifficulty() : "medium")
                .totalQuestions(drafts.size())
                .createdAtUtc(Instant.now())
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

        QuizAttempt latestAttempt = quizAttemptRepository
                .findTopByQuizAndUserIdOrderByCreatedAtUtcDesc(quiz, user.getId())
                .orElse(null);
        if (latestAttempt != null && Boolean.TRUE.equals(latestAttempt.getPassedFlag())) {
            throw new IllegalStateException("Quiz already passed");
        }

        Instant attemptWindowStart = resolveAttemptWindowStart(quiz.getSessionId());
        long failedAttemptsInWindow = quizAttemptRepository.countFailedAttemptsForSessionSince(
                user.getId(),
                quiz.getSessionId(),
                attemptWindowStart);
        if (failedAttemptsInWindow >= maxFailedAttempts) {
            throw new IllegalStateException(
                    "Maximum failed quiz attempts reached. Rewatch the video and analyze again (engagement >= "
                            + formatEngagementThresholdForMessage() + ") to unlock new attempts.");
        }

        List<QuizQuestion> questions = quizQuestionRepository.findByQuizOrderByPositionIndexAsc(quiz);
        int total = questions.size();
        int correct = 0;
        Map<String, String> providedAnswers = req.getAnswers() == null ? Map.of() : req.getAnswers();

        for (QuizQuestion q : questions) {
            String provided = resolveProvidedAnswer(providedAnswers, q);
            if (isCorrectAnswer(q, provided)) {
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

            // Update session status to CERTIFIED
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
                .scorePercent(score)
                .passed(passed)
                .certificateId(certId)
                .verificationLink(verifyLink)
                .build();
    }

    private Instant resolveAttemptWindowStart(String sessionId) {
        EngagementResult latest = engagementResultRepository.findTopBySessionIdOrderByCreatedAtUtcDesc(sessionId)
                .orElseThrow(() -> new IllegalStateException("Analyze session first"));

        if (latest.getEngagementScore() == null || latest.getEngagementScore() < minEngagementScore) {
            throw new IllegalStateException("Engagement score is below quiz eligibility threshold");
        }
        return latest.getCreatedAtUtc() == null ? Instant.EPOCH : latest.getCreatedAtUtc();
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
                .verificationLink(cert == null ? null
                        : (publicBaseUrl + "/api/certificates/verify/" + cert.getVerificationToken()))
                .build();
    }

    // --- STEM check ---
    private boolean checkStemEligible(String videoId) {
        YouTubeVideoCache videoCache = videoCacheRepository.findByVideoId(videoId).orElse(null);
        return videoCache != null && StemCategoryUtil.isStemCategory(videoCache.getCategoryId());
    }

    // --- Question extraction from ML response ---
    private List<QuestionDraft> extractQuestions(Map<String, Object> ml) {
        Object raw = ml.get("questions");
        if (!(raw instanceof List<?> list))
            return List.of();

        List<QuestionDraft> out = new ArrayList<>();
        for (Object obj : list) {
            if (!(obj instanceof Map<?, ?> m))
                continue;

            String qId = str(m.get("question_id"), null);
            String qType = str(m.get("type"), "mcq");
            String qText = str(m.get("question"), "");
            List<String> options = toStringList(m.get("options"));
            // ML returns "correct_answer", fallback to "answer" for compatibility
            String answer = str(m.get("correct_answer"), str(m.get("answer"), ""));
            String explanation = str(m.get("explanation"), "");
            String difficulty = str(m.get("difficulty"), "medium");

            if (qText.isBlank() || answer.isBlank())
                continue;
            out.add(new QuestionDraft(qId, qType, qText, options, answer, explanation, difficulty));
        }
        return out;
    }

    private List<String> toStringList(Object o) {
        if (!(o instanceof List<?> list))
            return List.of();
        List<String> out = new ArrayList<>();
        for (Object v : list) {
            if (v != null && !v.toString().isBlank())
                out.add(v.toString());
        }
        return out;
    }

    private String str(Object v, String def) {
        if (v == null)
            return def;
        String s = v.toString().trim();
        return s.isBlank() ? def : s;
    }

    private String normalizeAnswer(String s) {
        if (s == null)
            return "";
        return s.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    private boolean isCorrectAnswer(QuizQuestion question, String providedAnswerRaw) {
        List<String> options = readOptions(question.getOptionsJson());
        String expected = canonicalizeAnswer(question.getCorrectAnswer(), options);
        String provided = canonicalizeAnswer(providedAnswerRaw, options);
        return !expected.isBlank() && expected.equals(provided);
    }

    private String canonicalizeAnswer(String answerRaw, List<String> options) {
        String normalized = normalizeAnswer(answerRaw);
        if (normalized.isBlank())
            return "";

        if (!options.isEmpty()) {
            int optionIndex = resolveOptionIndex(normalized, options);
            if (optionIndex >= 0 && optionIndex < options.size()) {
                normalized = normalizeAnswer(options.get(optionIndex));
            }
        }

        String normalizedBool = normalizeBooleanToken(normalized);
        return normalizedBool.isBlank() ? normalized : normalizedBool;
    }

    private int resolveOptionIndex(String normalizedInput, List<String> options) {
        if (normalizedInput == null || normalizedInput.isBlank())
            return -1;

        for (int i = 0; i < options.size(); i++) {
            if (normalizeAnswer(options.get(i)).equals(normalizedInput)) {
                return i;
            }
        }

        Matcher optionMatcher = OPTION_PREFIX_PATTERN.matcher(normalizedInput);
        if (optionMatcher.matches()) {
            int idx = optionMatcher.group(1).charAt(0) - 'a';
            if (idx >= 0 && idx < options.size())
                return idx;
        }

        Matcher letterMatcher = LETTER_CHOICE_PATTERN.matcher(normalizedInput);
        if (letterMatcher.matches()) {
            int idx = letterMatcher.group(1).charAt(0) - 'a';
            if (idx >= 0 && idx < options.size())
                return idx;
        }

        Matcher numberMatcher = NUMBER_CHOICE_PATTERN.matcher(normalizedInput);
        if (numberMatcher.matches()) {
            int idx = Integer.parseInt(numberMatcher.group(1)) - 1;
            if (idx >= 0 && idx < options.size())
                return idx;
        }

        return -1;
    }

    private String normalizeBooleanToken(String value) {
        return switch (value) {
            case "true", "t", "yes", "y" -> "true";
            case "false", "f", "no", "n" -> "false";
            default -> "";
        };
    }

    private String resolveProvidedAnswer(Map<String, String> answers, QuizQuestion question) {
        String direct = answers.get(question.getQuestionUid());
        if (direct != null)
            return direct;

        String qKey = "q" + question.getPositionIndex();
        if (answers.containsKey(qKey))
            return answers.get(qKey);

        String posKey = String.valueOf(question.getPositionIndex());
        if (answers.containsKey(posKey))
            return answers.get(posKey);

        for (Map.Entry<String, String> entry : answers.entrySet()) {
            String key = entry.getKey();
            if (key == null)
                continue;
            if (key.equalsIgnoreCase(question.getQuestionUid()) || key.equalsIgnoreCase(qKey)
                    || key.equalsIgnoreCase(posKey)) {
                return entry.getValue();
            }
        }
        return null;
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
        if (json == null || json.isBlank())
            return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private record QuestionDraft(
            String questionId,
            String questionType,
            String questionText,
            List<String> options,
            String correctAnswer,
            String explanation,
            String difficulty) {
    }
}
