package com.certifytube.backend.service;

import com.certifytube.backend.model.Session;
import com.certifytube.backend.model.SessionEvent;
import com.certifytube.backend.repository.SessionEventRepository;
import com.certifytube.backend.repository.SessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FeatureEngineeringServiceImpl implements FeatureEngineeringService {

    private final SessionEventRepository eventRepository;
    private final SessionRepository sessionRepository;

    private final ObjectMapper mapper = new ObjectMapper();
    private volatile List<String> contractKeys;

    private static final double EPS = 1e-6;
    private static final double LONG_PAUSE_THRESHOLD_SEC = 10.0;
    private static final long MAX_CLIENT_SERVER_DRIFT_SEC = 120;

    private List<String> getContractKeys() {
        if (contractKeys != null) return contractKeys;

        synchronized (this) {
            if (contractKeys != null) return contractKeys;
            try {
                ClassPathResource res = new ClassPathResource("feature_contract_v1.json");
                try (InputStream is = res.getInputStream()) {
                    JsonNode root = mapper.readTree(is);
                    JsonNode feats = root.get("features");
                    if (feats == null || !feats.isArray()) {
                        throw new IllegalStateException("feature_contract_v1.json missing 'features' array");
                    }
                    List<String> keys = new ArrayList<>();
                    feats.forEach(n -> keys.add(n.asText()));
                    contractKeys = List.copyOf(keys);
                    return contractKeys;
                }
            } catch (Exception e) {
                throw new RuntimeException("Cannot load feature_contract_v1.json", e);
            }
        }
    }

    private void putIfExists(Map<String, Object> f, String key, double val) {
        if (f.containsKey(key)) f.put(key, sanitize(val));
    }

    private Map<String, Object> initFeatureMap() {
        Map<String, Object> f = new LinkedHashMap<>();
        for (String k : getContractKeys()) f.put(k, 0.0);
        return f;
    }

    private double sanitize(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return value;
    }

    private double safeDivide(double numerator, double denominator) {
        if (Math.abs(denominator) < EPS) return 0.0;
        return sanitize(numerator / denominator);
    }

    private void sanitizeAllFeatures(Map<String, Object> features) {
        for (Map.Entry<String, Object> entry : features.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Number n) {
                entry.setValue(sanitize(n.doubleValue()));
            } else {
                try {
                    entry.setValue(sanitize(Double.parseDouble(String.valueOf(value))));
                } catch (Exception ignored) {
                    entry.setValue(0.0);
                }
            }
        }
    }

    private double resolveVideoDurationFromSession(String sessionId) {
        Optional<Session> sessionOpt = sessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty() || sessionOpt.get().getVideoDurationSec() == null) {
            return 0.0;
        }
        return Math.max(sessionOpt.get().getVideoDurationSec(), 0.0);
    }

    private double resolveVideoDuration(List<SessionEvent> events, String sessionId) {
        double fromEvents = events.stream()
                .map(SessionEvent::getVideoDurationSec)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);
        if (fromEvents > 0.0) {
            return fromEvents;
        }
        return resolveVideoDurationFromSession(sessionId);
    }

    @Override
    public Map<String, Object> computeFeaturesForSession(String sessionId) {
        Map<String, Object> f = initFeatureMap();
        List<SessionEvent> events = eventRepository.findBySessionIdOrderByCreatedAtUtcAsc(sessionId);
        if (events == null || events.isEmpty()) {
            putIfExists(f, "video_duration_sec", resolveVideoDurationFromSession(sessionId));
            sanitizeAllFeatures(f);
            return f;
        }

        TrustedTimeline trustedTimeline = buildTrustedTimeline(events);
        List<TrustedEvent> timelineEvents = trustedTimeline.events();
        if (timelineEvents.isEmpty()) {
            putIfExists(f, "video_duration_sec", resolveVideoDuration(events, sessionId));
            sanitizeAllFeatures(f);
            return f;
        }

        long numPause = events.stream().filter(e -> "pause".equalsIgnoreCase(e.getEventType())).count();
        long numRateChange = events.stream().filter(e -> "ratechange".equalsIgnoreCase(e.getEventType())).count();
        long numBuffer = events.stream().filter(e -> "buffering".equalsIgnoreCase(e.getEventType())).count();

        LocalDateTime start = timelineEvents.get(0).effectiveTs();
        LocalDateTime end = timelineEvents.get(timelineEvents.size() - 1).effectiveTs();
        double sessionDurationSec = Math.max(Duration.between(start, end).toMillis() / 1000.0, 0.0);

        double videoDurationSec = resolveVideoDuration(events, sessionId);

        double lastPositionSec = 0.0;
        for (TrustedEvent te : timelineEvents) {
            Double p = te.row().getCurrentTimeSec();
            if (p != null) lastPositionSec = p;
        }

        double watchTimeSec = 0.0;
        double totalPauseDurationSec = 0.0;
        double bufferingTimeSec = 0.0;

        double timeLt1x = 0.0, time1x = 0.0, timeGt1x = 0.0;
        double weightedRateSum = 0.0;
        List<Double> playbackRateSamples = new ArrayList<>();

        int numSeekForward = 0;
        int numSeekBackward = 0;
        double totalSeekForwardSec = 0.0;
        double totalSeekBackwardSec = 0.0;
        double largestForwardSeekSec = 0.0;
        double largestBackwardSeekSec = 0.0;
        List<Double> seekDistances = new ArrayList<>();
        Double firstSeekTimeSec = null; // elapsed seconds from session start
        Boolean firstSeekForward = null;

        List<Double> pauseDurations = new ArrayList<>();

        LocalDateTime lastTs = null;
        Integer lastState = null; // 1=playing,2=paused,3=buffering
        double lastRate = 1.0;
        double lastPos = 0.0;

        for (TrustedEvent te : timelineEvents) {
            SessionEvent row = te.row();
            LocalDateTime curTs = te.effectiveTs();
            Integer curState = row.getPlayerState();
            Double curRateObj = row.getPlaybackRate();
            Double curPosObj = row.getCurrentTimeSec();

            if (curRateObj != null) {
                playbackRateSamples.add(curRateObj);
            }

            double curRate = (curRateObj != null ? curRateObj : lastRate);
            double curPos = (curPosObj != null ? curPosObj : lastPos);

            if (lastTs != null) {
                double dt = Math.max(Duration.between(lastTs, curTs).toMillis() / 1000.0, 0.0);

                if (lastState != null && lastState == 1) {
                    watchTimeSec += dt;
                    weightedRateSum += (lastRate * dt);

                    if (lastRate < 1.0) timeLt1x += dt;
                    else if (lastRate > 1.0) timeGt1x += dt;
                    else time1x += dt;

                } else if (lastState != null && lastState == 2) {
                    totalPauseDurationSec += dt;
                    pauseDurations.add(dt);

                } else if (lastState != null && lastState == 3) {
                    bufferingTimeSec += dt;
                }
            }

            if ("seek".equalsIgnoreCase(row.getEventType())) {
                if (firstSeekTimeSec == null) {
                    firstSeekTimeSec = Math.max(Duration.between(start, curTs).toMillis() / 1000.0, 0.0);
                }

                Double from = row.getSeekFromSec();
                Double to = row.getSeekToSec();

                double delta;
                if (from != null && to != null) delta = to - from;
                else delta = curPos - lastPos;

                if (delta > 0.0) {
                    if (firstSeekForward == null) {
                        firstSeekForward = true;
                    }
                    numSeekForward += 1;
                    totalSeekForwardSec += delta;
                    largestForwardSeekSec = Math.max(largestForwardSeekSec, delta);
                    seekDistances.add(Math.abs(delta));
                } else if (delta < 0.0) {
                    if (firstSeekForward == null) {
                        firstSeekForward = false;
                    }
                    double d = Math.abs(delta);
                    numSeekBackward += 1;
                    totalSeekBackwardSec += d;
                    largestBackwardSeekSec = Math.max(largestBackwardSeekSec, d);
                    seekDistances.add(d);
                }
            }

            lastTs = curTs;
            if (curState != null) lastState = curState;
            lastRate = curRate;
            lastPos = curPos;
        }

        int longPauseCount = (int) pauseDurations.stream().filter(d -> d > LONG_PAUSE_THRESHOLD_SEC).count();
        int totalSeeks = numSeekForward + numSeekBackward;
        double completedFlag = (videoDurationSec > 0.0 && lastPositionSec >= 0.95 * videoDurationSec) ? 1.0 : 0.0;
        double watchTimeRatio = safeDivide(watchTimeSec, sessionDurationSec);
        double completionRatio = safeDivide(lastPositionSec, videoDurationSec);
        double engagementVelocity = safeDivide(watchTimeSec, sessionDurationSec);
        double pauseFreqPerMin = safeDivide(numPause * 60.0, sessionDurationSec);
        double avgPauseDuration = (numPause > 0 ? totalPauseDurationSec / numPause : 0.0);

        double medianPause = 0.0;
        if (!pauseDurations.isEmpty()) {
            List<Double> sorted = new ArrayList<>(pauseDurations);
            sorted.sort(Double::compareTo);
            int mid = sorted.size() / 2;
            medianPause = (sorted.size() % 2 == 0)
                    ? (sorted.get(mid - 1) + sorted.get(mid)) / 2.0
                    : sorted.get(mid);
        }
        double longPauseRatio = safeDivide(longPauseCount, numPause);

        double skipTimeRatio = safeDivide(totalSeekForwardSec, videoDurationSec);
        double rewatchTimeRatio = safeDivide(totalSeekBackwardSec, videoDurationSec);
        double rewatchToSkipRatio = totalSeekForwardSec > 0.0
                ? safeDivide(totalSeekBackwardSec, totalSeekForwardSec)
                : 0.0;
        double seekDensityPerMin = safeDivide(totalSeeks * 60.0, watchTimeSec);

        double avgSeekForward = (numSeekForward > 0 ? totalSeekForwardSec / numSeekForward : 0.0);
        double avgSeekBackward = (numSeekBackward > 0 ? totalSeekBackwardSec / numSeekBackward : 0.0);

        double seekForwardRatio = safeDivide(numSeekForward, totalSeeks);
        double seekBackwardRatio = safeDivide(numSeekBackward, totalSeeks);

        double seekJumpStd = 0.0;
        if (seekDistances.size() >= 2) {
            double mean = seekDistances.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double sum = 0.0;
            for (double v : seekDistances) sum += (v - mean) * (v - mean);
            seekJumpStd = Math.sqrt(sum / seekDistances.size());
        }

        double fastRatio = safeDivide(timeGt1x, watchTimeSec);
        double slowRatio = safeDivide(timeLt1x, watchTimeSec);
        double avgPlaybackRateWhenPlaying = safeDivide(weightedRateSum, watchTimeSec);

        Set<Double> speedSet = new HashSet<>();
        for (Double rate : playbackRateSamples) {
            speedSet.add(rate);
        }
        double uniqueSpeedLevels = speedSet.size();

        double playbackSpeedVariance = 0.0;
        if (playbackRateSamples.size() >= 2) {
            double mean = playbackRateSamples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double sum = 0.0;
            for (double sample : playbackRateSamples) {
                double diff = sample - mean;
                sum += diff * diff;
            }
            playbackSpeedVariance = safeDivide(sum, playbackRateSamples.size());
        }

        double bufferingFreqPerMin = safeDivide(numBuffer * 60.0, sessionDurationSec);
        double playPauseRatio = safeDivide(watchTimeSec, totalPauseDurationSec);
        double firstSeekTime = (firstSeekTimeSec != null ? firstSeekTimeSec : 0.0);
        int earlySkipFlag = (Boolean.TRUE.equals(firstSeekForward)
                && videoDurationSec > 0.0
                && firstSeekTime <= 0.1 * videoDurationSec) ? 1 : 0;
        double attentionIndex = watchTimeRatio * (1.0 - skipTimeRatio);
        int skimFlag = skipTimeRatio > 0.3 ? 1 : 0;
        int deepFlag = rewatchTimeRatio > 0.05 ? 1 : 0;

        putIfExists(f, "session_duration_sec", sessionDurationSec);
        putIfExists(f, "video_duration_sec", videoDurationSec);
        putIfExists(f, "last_position_sec", lastPositionSec);
        putIfExists(f, "completed_flag", completedFlag);

        putIfExists(f, "watch_time_sec", watchTimeSec);
        putIfExists(f, "watch_time_ratio", watchTimeRatio);
        putIfExists(f, "completion_ratio", completionRatio);
        putIfExists(f, "engagement_velocity", engagementVelocity);

        putIfExists(f, "num_pause", (double) numPause);
        putIfExists(f, "total_pause_duration_sec", totalPauseDurationSec);
        putIfExists(f, "avg_pause_duration_sec", avgPauseDuration);
        putIfExists(f, "median_pause_duration_sec", medianPause);
        putIfExists(f, "pause_freq_per_min", pauseFreqPerMin);
        putIfExists(f, "long_pause_count", (double) longPauseCount);
        putIfExists(f, "long_pause_ratio", longPauseRatio);

        putIfExists(f, "num_seek", (double) totalSeeks);
        putIfExists(f, "num_seek_forward", (double) numSeekForward);
        putIfExists(f, "num_seek_backward", (double) numSeekBackward);
        putIfExists(f, "total_seek_forward_sec", totalSeekForwardSec);
        putIfExists(f, "total_seek_backward_sec", totalSeekBackwardSec);
        putIfExists(f, "avg_seek_forward_sec", avgSeekForward);
        putIfExists(f, "avg_seek_backward_sec", avgSeekBackward);
        putIfExists(f, "largest_forward_seek_sec", largestForwardSeekSec);
        putIfExists(f, "largest_backward_seek_sec", largestBackwardSeekSec);
        putIfExists(f, "seek_jump_std_sec", seekJumpStd);
        putIfExists(f, "seek_forward_ratio", seekForwardRatio);
        putIfExists(f, "seek_backward_ratio", seekBackwardRatio);
        putIfExists(f, "skip_time_ratio", skipTimeRatio);
        putIfExists(f, "rewatch_time_ratio", rewatchTimeRatio);
        putIfExists(f, "rewatch_to_skip_ratio", rewatchToSkipRatio);
        putIfExists(f, "seek_density_per_min", seekDensityPerMin);
        putIfExists(f, "first_seek_time_sec", firstSeekTime);
        putIfExists(f, "early_skip_flag", earlySkipFlag);

        putIfExists(f, "num_ratechange", (double) numRateChange);
        putIfExists(f, "time_at_speed_lt1x_sec", timeLt1x);
        putIfExists(f, "time_at_speed_1x_sec", time1x);
        putIfExists(f, "time_at_speed_gt1x_sec", timeGt1x);
        putIfExists(f, "fast_ratio", fastRatio);
        putIfExists(f, "slow_ratio", slowRatio);
        putIfExists(f, "playback_speed_variance", playbackSpeedVariance);
        putIfExists(f, "avg_playback_rate_when_playing", avgPlaybackRateWhenPlaying);
        putIfExists(f, "unique_speed_levels", uniqueSpeedLevels);

        putIfExists(f, "num_buffering_events", (double) numBuffer);
        putIfExists(f, "buffering_time_sec", bufferingTimeSec);
        putIfExists(f, "buffering_freq_per_min", bufferingFreqPerMin);

        putIfExists(f, "play_pause_ratio", playPauseRatio);
        putIfExists(f, "attention_index", attentionIndex);
        putIfExists(f, "skim_flag", skimFlag);
        putIfExists(f, "deep_flag", deepFlag);

        sanitizeAllFeatures(f);
        return f;
    }

    private TrustedTimeline buildTrustedTimeline(List<SessionEvent> events) {
        List<TrustedEvent> out = new ArrayList<>();
        if (events.isEmpty()) return new TrustedTimeline(out, 0, 0, 0);

        long firstServerMs = Optional.ofNullable(events.get(0).getCreatedAtUtc())
                .map(ts -> ts.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .orElse(System.currentTimeMillis());
        Long firstClientMs = null;
        Long prevNormalizedClientMs = null;
        LocalDateTime prevEffectiveTs = null;

        int nonMonotonicClientCount = 0;
        int driftViolationCount = 0;
        int fallbackToServerCount = 0;

        for (SessionEvent row : events) {
            LocalDateTime serverTs = Optional.ofNullable(row.getCreatedAtUtc()).orElse(LocalDateTime.now());
            Long clientMs = row.getClientEventMs();
            Long normalizedClientMs = null;

            if (clientMs != null && clientMs >= 0) {
                if (firstClientMs == null) firstClientMs = clientMs;
                normalizedClientMs = firstServerMs + (clientMs - firstClientMs);

                if (prevNormalizedClientMs != null && normalizedClientMs < prevNormalizedClientMs) {
                    nonMonotonicClientCount += 1;
                    normalizedClientMs = null;
                } else if (normalizedClientMs != null) {
                    prevNormalizedClientMs = normalizedClientMs;
                }
            }

            LocalDateTime effectiveTs = serverTs;
            if (normalizedClientMs != null) {
                long driftMs = Math.abs(normalizedClientMs - serverTs.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                if (driftMs <= MAX_CLIENT_SERVER_DRIFT_SEC * 1000) {
                    effectiveTs = LocalDateTime.ofInstant(Instant.ofEpochMilli(normalizedClientMs), ZoneId.systemDefault());
                } else {
                    driftViolationCount += 1;
                    fallbackToServerCount += 1;
                }
            } else if (clientMs != null) {
                fallbackToServerCount += 1;
            }

            if (prevEffectiveTs != null && effectiveTs.isBefore(prevEffectiveTs)) {
                effectiveTs = prevEffectiveTs;
                fallbackToServerCount += 1;
            }

            prevEffectiveTs = effectiveTs;
            out.add(new TrustedEvent(row, effectiveTs));
        }

        return new TrustedTimeline(out, nonMonotonicClientCount, driftViolationCount, fallbackToServerCount);
    }

    private record TrustedEvent(SessionEvent row, LocalDateTime effectiveTs) {}

    private record TrustedTimeline(
            List<TrustedEvent> events,
            int nonMonotonicClientCount,
            int driftViolationCount,
            int fallbackToServerCount
    ) {}
}
