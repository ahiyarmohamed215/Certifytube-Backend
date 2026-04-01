package com.certifytube.backend.service;

import com.certifytube.backend.client.MlServiceClient;
import com.certifytube.backend.dto.SessionAnalyzeResponse;
import com.certifytube.backend.dto.SystemFlowDto;
import com.certifytube.backend.model.EngagementResult;
import com.certifytube.backend.model.Session;
import com.certifytube.backend.model.SessionEvent;
import com.certifytube.backend.model.UserAccount;
import com.certifytube.backend.model.YouTubeVideoCache;
import com.certifytube.backend.repository.EngagementResultRepository;
import com.certifytube.backend.repository.SessionEventRepository;
import com.certifytube.backend.repository.SessionRepository;
import com.certifytube.backend.repository.YouTubeVideoCacheRepository;
import com.certifytube.backend.util.StemCategoryUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.certifytube.backend.util.SystemFlowUtil.data;
import static com.certifytube.backend.util.SystemFlowUtil.flow;
import static com.certifytube.backend.util.SystemFlowUtil.step;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionAnalyzeServiceImpl implements SessionAnalyzeService {

    private final EngagementResultRepository engagementResultRepository;
    private final SessionService sessionService;
    private final SessionRepository sessionRepository;
    private final SessionEventRepository sessionEventRepository;
    private final AuthenticatedUserService authenticatedUserService;
    private final MlServiceClient mlServiceClient;
    private final YouTubeVideoCacheRepository videoCacheRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${ml.feature-version}")
    private String featureVersion;

    @Value("${ml.engagement-threshold}")
    private double engagementThreshold;

    @Value("${ml.default-model}")
    private String defaultModel;

    /**
     * Idempotency window: if already analyzed within this many seconds, return
     * cached result.
     */
    private static final long IDEMPOTENCY_WINDOW_SEC = 60;

    @Override
    @SuppressWarnings("unchecked")
    public SessionAnalyzeResponse analyzeSession(String sessionId, String model) {
        UserAccount user = authenticatedUserService.currentUser();
        Session session = sessionService.getById(sessionId);
        if (!String.valueOf(user.getId()).equals(session.getUserId())) {
            throw new AccessDeniedException("Session does not belong to authenticated user");
        }
        if (session.getEndedAtUtc() == null) {
            throw new IllegalStateException("Session must be ended before analyze. Call /api/sessions/end first");
        }

        // --- STEM gate: block non-STEM videos ---
        YouTubeVideoCache videoCache = videoCacheRepository.findByVideoId(session.getVideoId()).orElse(null);
        if (!StemCategoryUtil.isStemVideo(videoCache)) {
            throw new IllegalStateException(
                    "Engagement analysis is only available for STEM-based skill videos. "
                            + "This video is not eligible for certification.");
        }

        // --- Idempotency: if recently analyzed, return cached result ---
        Optional<EngagementResult> recent = engagementResultRepository
                .findTopBySessionIdOrderByCreatedAtUtcDesc(sessionId);
        if (recent.isPresent()) {
            EngagementResult cached = recent.get();
            if (cached.getCreatedAtUtc() != null
                    && Duration.between(cached.getCreatedAtUtc(), LocalDateTime.now())
                            .getSeconds() < IDEMPOTENCY_WINDOW_SEC) {
                return SessionAnalyzeResponse.builder()
                        .sessionId(sessionId)
                        .model(cached.getModelUsed())
                        .engagementScore(cached.getEngagementScore())
                        .threshold(cached.getThreshold())
                        .status(cached.getStatus())
                        .explanation(cached.getExplanation())
                        .systemFlow(buildCachedFlow(sessionId, cached))
                        .build();
            }
        }

        String resolvedModel = (model != null && !model.isBlank()) ? model.toLowerCase() : defaultModel;
        if (!"xgboost".equals(resolvedModel) && !"ebm".equals(resolvedModel)) {
            throw new IllegalArgumentException("Invalid model: " + resolvedModel + ". Must be 'xgboost' or 'ebm'");
        }

        List<SessionEvent> events = sessionEventRepository.findBySessionIdOrderByCreatedAtUtcAsc(sessionId);
        if (events.isEmpty()) {
            throw new IllegalStateException("No session events found. Record events before engagement analysis.");
        }
        List<Map<String, Object>> mlEvents = events.stream()
                .map(this::mapEventToMlContract)
                .toList();

        log.info("session.analyze.ml.request sessionId={} model={} featureVersion={} eventCount={}",
                sessionId, resolvedModel, featureVersion, mlEvents.size());
        Map<String, Object> ml = mlServiceClient.analyzeEngagement(
                sessionId,
                featureVersion,
                mlEvents,
                resolvedModel,
                engagementThreshold);

        double score = asDouble(ml.get("engagement_score"));
        String explanation = normalizeText(ml.get("explanation"));

        Object topPositive;
        Object topNegative;
        if ("xgboost".equals(resolvedModel)) {
            topPositive = ml.get("shap_top_positive");
            topNegative = ml.get("shap_top_negative");
        } else {
            topPositive = ml.get("ebm_top_positive");
            topNegative = ml.get("ebm_top_negative");
        }

        String status = score >= engagementThreshold ? "ENGAGED" : "NOT_ENGAGED";
        String nextSessionStatus = "ENGAGED".equals(status) ? "QUIZ_PENDING" : session.getStatus();

        try {
            engagementResultRepository.save(EngagementResult.builder()
                    .sessionId(sessionId)
                    .modelUsed(resolvedModel)
                    .engagementScore(score)
                    .threshold(engagementThreshold)
                    .status(status)
                    .explanation(explanation)
                    .topPositiveJson(mapper.writeValueAsString(topPositive))
                    .topNegativeJson(mapper.writeValueAsString(topNegative))
                    .createdAtUtc(LocalDateTime.now())
                    .build());
            log.info("session.analyze.completed sessionId={} model={} status={} score={} eventCount={}",
                    sessionId, resolvedModel, status, score, mlEvents.size());
        } catch (Exception e) {
            throw new RuntimeException("Failed to save ML engagement result", e);
        }

        session.setStatus(nextSessionStatus);
        sessionRepository.save(session);

        return SessionAnalyzeResponse.builder()
                .sessionId(sessionId)
                .model(resolvedModel)
                .engagementScore(score)
                .threshold(engagementThreshold)
                .status(status)
                .explanation(explanation)
                .systemFlow(buildAnalyzeFlow(
                        sessionId,
                        resolvedModel,
                        mlEvents,
                        score,
                        explanation,
                        status,
                        nextSessionStatus))
                .build();
    }

    private Map<String, Object> mapEventToMlContract(SessionEvent event) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("event_id", event.getEventId());
        out.put("session_id", event.getSessionId());
        out.put("user_id", event.getUserId());
        out.put("video_id", event.getVideoId());
        out.put("video_title", event.getVideoTitle());
        out.put("event_type", event.getEventType());
        out.put("player_state", event.getPlayerState());
        out.put("playback_rate", event.getPlaybackRate());
        out.put("current_time_sec", event.getCurrentTimeSec());
        out.put("video_duration_sec", event.getVideoDurationSec());
        out.put("created_at_utc", formatCreatedAtUtc(event.getCreatedAtUtc()));
        out.put("client_created_at_local", event.getClientCreatedAtLocal());
        out.put("client_tz_offset_min", event.getClientTzOffsetMin());
        out.put("seek_from_sec", event.getSeekFromSec());
        out.put("seek_to_sec", event.getSeekToSec());
        return out;
    }

    private String formatCreatedAtUtc(LocalDateTime createdAtUtc) {
        if (createdAtUtc == null) {
            return null;
        }
        return createdAtUtc.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private double asDouble(Object o) {
        if (o == null)
            return 0.0;
        if (o instanceof Number n)
            return n.doubleValue();
        return Double.parseDouble(o.toString());
    }

    private String normalizeText(Object value) {
        if (value == null) {
            return "";
        }
        return value.toString().trim();
    }

    private SystemFlowDto buildCachedFlow(String sessionId, EngagementResult cached) {
        return flow("session-analyze", List.of(
                step(
                        "load-cached-analysis",
                        "cached",
                        "Returned a recently saved engagement result instead of calling ML again.",
                        data(
                                "sessionId", sessionId,
                                "model", cached.getModelUsed(),
                                "status", cached.getStatus(),
                                "engagementScore", cached.getEngagementScore(),
                                "threshold", cached.getThreshold(),
                                "cachedAtUtc", cached.getCreatedAtUtc()))));
    }

    private SystemFlowDto buildAnalyzeFlow(
            String sessionId,
            String model,
            List<Map<String, Object>> mlEvents,
            double score,
            String explanation,
            String status,
            String nextSessionStatus) {

        return flow("session-analyze", List.of(
                step(
                        "prepare-ml-payload",
                        "completed",
                        "Prepared the engagement payload that will be sent to ML.",
                        data(
                                "sessionId", sessionId,
                                "model", model,
                                "featureVersion", featureVersion,
                                "eventCount", mlEvents.size(),
                                "firstEventType", firstEventType(mlEvents),
                                "lastEventType", lastEventType(mlEvents))),
                step(
                        "call-ml",
                        "completed",
                        "Sent the engagement analysis request to the ML service.",
                        data(
                                "endpoint", "/engagement/analyze/" + model,
                                "payloadSummary", data(
                                        "sessionId", sessionId,
                                        "featureVersion", featureVersion,
                                        "engagementThreshold", engagementThreshold,
                                        "eventCount", mlEvents.size()))),
                step(
                        "finalize-result",
                        "completed",
                        "Saved the ML result and updated the session state.",
                        data(
                                "status", status,
                                "engagementScore", score,
                                "threshold", engagementThreshold,
                                "sessionStatus", nextSessionStatus,
                                "explanationPreview", truncate(explanation, 180)))));
    }

    private String firstEventType(List<Map<String, Object>> mlEvents) {
        if (mlEvents == null || mlEvents.isEmpty()) {
            return null;
        }
        Object value = mlEvents.get(0).get("event_type");
        return value == null ? null : value.toString();
    }

    private String lastEventType(List<Map<String, Object>> mlEvents) {
        if (mlEvents == null || mlEvents.isEmpty()) {
            return null;
        }
        Object value = mlEvents.get(mlEvents.size() - 1).get("event_type");
        return value == null ? null : value.toString();
    }

    private String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "...";
    }
}
