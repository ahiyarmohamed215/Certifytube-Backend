package com.certifytube.backend.service;

import com.certifytube.backend.client.MlServiceClient;
import com.certifytube.backend.dto.SessionAnalyzeResponse;
import com.certifytube.backend.mapper.SessionFeaturesMapper;
import com.certifytube.backend.model.EngagementResult;
import com.certifytube.backend.model.Session;
import com.certifytube.backend.model.SessionFeatures;
import com.certifytube.backend.model.UserAccount;
import com.certifytube.backend.model.YouTubeVideoCache;
import com.certifytube.backend.repository.EngagementResultRepository;
import com.certifytube.backend.repository.SessionFeaturesRepository;
import com.certifytube.backend.repository.SessionRepository;
import com.certifytube.backend.repository.YouTubeVideoCacheRepository;
import com.certifytube.backend.util.StemCategoryUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SessionAnalyzeServiceImpl implements SessionAnalyzeService {

    private final FeatureEngineeringService featureEngineeringService;
    private final EngagementResultRepository engagementResultRepository;
    private final SessionFeaturesRepository sessionFeaturesRepository;
    private final SessionService sessionService;
    private final SessionRepository sessionRepository;
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

    /** The 49 feature keys that the ML API accepts. */
    private static final Set<String> ML_CONTRACT_KEYS = Set.of(
            "session_duration_sec", "video_duration_sec", "last_position_sec", "completed_flag",
            "watch_time_sec", "watch_time_ratio", "completion_ratio", "engagement_velocity",
            "num_pause", "total_pause_duration_sec", "avg_pause_duration_sec", "median_pause_duration_sec",
            "pause_freq_per_min", "long_pause_count", "long_pause_ratio",
            "num_seek", "num_seek_forward", "num_seek_backward",
            "total_seek_forward_sec", "total_seek_backward_sec",
            "avg_seek_forward_sec", "avg_seek_backward_sec",
            "largest_forward_seek_sec", "largest_backward_seek_sec", "seek_jump_std_sec",
            "seek_forward_ratio", "seek_backward_ratio",
            "skip_time_ratio", "rewatch_time_ratio", "rewatch_to_skip_ratio",
            "seek_density_per_min", "first_seek_time_sec", "early_skip_flag",
            "num_ratechange",
            "time_at_speed_lt1x_sec", "time_at_speed_1x_sec", "time_at_speed_gt1x_sec",
            "fast_ratio", "slow_ratio", "playback_speed_variance",
            "avg_playback_rate_when_playing", "unique_speed_levels",
            "num_buffering_events", "buffering_time_sec", "buffering_freq_per_min",
            "play_pause_ratio", "attention_index", "skim_flag", "deep_flag");

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
                    && Duration.between(cached.getCreatedAtUtc(), Instant.now())
                            .getSeconds() < IDEMPOTENCY_WINDOW_SEC) {
                return SessionAnalyzeResponse.builder()
                        .sessionId(sessionId)
                        .model(cached.getModelUsed())
                        .engagementScore(cached.getEngagementScore())
                        .threshold(cached.getThreshold())
                        .status(cached.getStatus())
                        .explanation(cached.getExplanation())
                        .build();
            }
        }

        // Resolve model
        String resolvedModel = (model != null && !model.isBlank()) ? model.toLowerCase() : defaultModel;
        if (!"xgboost".equals(resolvedModel) && !"ebm".equals(resolvedModel)) {
            throw new IllegalArgumentException("Invalid model: " + resolvedModel + ". Must be 'xgboost' or 'ebm'");
        }

        // 1) Compute features from stored events
        Map<String, Object> allFeatures = featureEngineeringService.computeFeaturesForSession(sessionId);

        // Store ALL features (including timeline extras) to DB
        SessionFeatures entity = SessionFeaturesMapper.toEntity(sessionId, featureVersion, allFeatures);
        sessionFeaturesRepository.findBySessionId(sessionId).ifPresent(existing -> entity.setId(existing.getId()));
        sessionFeaturesRepository.save(entity);

        // Filter to only the 49 ML contract features
        Map<String, Object> mlFeatures = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : allFeatures.entrySet()) {
            if (ML_CONTRACT_KEYS.contains(entry.getKey())) {
                mlFeatures.put(entry.getKey(), entry.getValue());
            }
        }

        // 2) Call ML service
        Map<String, Object> ml = mlServiceClient.analyzeEngagement(sessionId, featureVersion, mlFeatures,
                resolvedModel);

        // 3) Extract response fields
        double score = asDouble(ml.get("engagement_score"));
        String explanation = String.valueOf(ml.getOrDefault("explanation", ""));

        // Extract contributors based on model
        Object topPositive;
        Object topNegative;
        if ("xgboost".equals(resolvedModel)) {
            topPositive = ml.get("shap_top_positive");
            topNegative = ml.get("shap_top_negative");
        } else {
            topPositive = ml.get("ebm_top_positive");
            topNegative = ml.get("ebm_top_negative");
        }

        // Backend applies threshold
        String status = score >= engagementThreshold ? "ENGAGED" : "NOT_ENGAGED";

        // 4) Save ML result
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
                    .createdAtUtc(Instant.now())
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to save ML engagement result", e);
        }

        // 5) Update session status
        if ("ENGAGED".equals(status)) {
            session.setStatus("QUIZ_PENDING");
        }
        // If NOT_ENGAGED, keep COMPLETED so user can rewatch
        sessionRepository.save(session);

        // 6) Build response for frontend
        return SessionAnalyzeResponse.builder()
                .sessionId(sessionId)
                .model(resolvedModel)
                .engagementScore(score)
                .threshold(engagementThreshold)
                .status(status)
                .explanation(explanation)
                .build();
    }

    private double asDouble(Object o) {
        if (o == null)
            return 0.0;
        if (o instanceof Number n)
            return n.doubleValue();
        return Double.parseDouble(o.toString());
    }
}
