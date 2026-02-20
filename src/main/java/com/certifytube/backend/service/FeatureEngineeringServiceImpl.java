package com.certifytube.backend.service;

import com.certifytube.backend.model.SessionEvent;
import com.certifytube.backend.repository.SessionEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FeatureEngineeringServiceImpl implements FeatureEngineeringService {

    private final SessionEventRepository eventRepository;

    private final ObjectMapper mapper = new ObjectMapper();
    private volatile List<String> contractKeys;

    private static final double EPS = 1e-6;
    private static final double LONG_PAUSE_THRESHOLD_SEC = 5.0;
    private static final double MAX_CREDITED_INTERVAL_SEC = 15.0;
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
        if (f.containsKey(key)) f.put(key, val);
    }

    @Override
    public Map<String, Object> computeFeaturesForSession(String sessionId) {

        List<SessionEvent> events = eventRepository.findBySessionIdOrderByCreatedAtUtcAsc(sessionId);
        if (events == null || events.isEmpty()) {
            throw new RuntimeException("No events found for session: " + sessionId);
        }

        TrustedTimeline trustedTimeline = buildTrustedTimeline(events);
        List<TrustedEvent> timelineEvents = trustedTimeline.events();
        if (timelineEvents.isEmpty()) {
            throw new RuntimeException("No valid timeline events found for session: " + sessionId);
        }

        Map<String, Object> f = new LinkedHashMap<>();
        for (String k : getContractKeys()) f.put(k, 0.0);

        long numPlay = events.stream().filter(e -> "play".equalsIgnoreCase(e.getEventType())).count();
        long numPause = events.stream().filter(e -> "pause".equalsIgnoreCase(e.getEventType())).count();
        long numSeekRaw = events.stream().filter(e -> "seek".equalsIgnoreCase(e.getEventType())).count();
        long numRateChange = events.stream().filter(e -> "ratechange".equalsIgnoreCase(e.getEventType())).count();
        long numBuffer = events.stream().filter(e -> "buffering".equalsIgnoreCase(e.getEventType())).count();
        boolean completed = events.stream().anyMatch(e -> "ended".equalsIgnoreCase(e.getEventType()));

        Instant start = timelineEvents.get(0).effectiveTs();
        Instant end = timelineEvents.get(timelineEvents.size() - 1).effectiveTs();
        double sessionDurationSec = Math.max(Duration.between(start, end).toMillis() / 1000.0, 0.0);

        double videoDurationSec = events.stream()
                .map(SessionEvent::getVideoDurationSec).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).max().orElse(0.0);

        double lastPositionSec = 0.0;
        for (TrustedEvent te : timelineEvents) {
            Double p = te.row().getCurrentTimeSec();
            if (p != null) lastPositionSec = p;
        }

        if (videoDurationSec <= 0) videoDurationSec = Math.max(lastPositionSec, 1.0);

        double watchTimeSec = 0.0;
        double totalPauseDurationSec = 0.0;
        double bufferingTimeSec = 0.0;

        double timeLt1x = 0.0, time1x = 0.0, timeGt1x = 0.0;
        double weightedRateSum = 0.0;

        int numSeekForward = 0;
        int numSeekBackward = 0;
        double totalSeekForwardSec = 0.0;
        double totalSeekBackwardSec = 0.0;
        double largestForwardSeekSec = 0.0;
        double largestBackwardSeekSec = 0.0;
        List<Double> seekDeltas = new ArrayList<>();
        Double firstSeekTimeSec = null;

        List<Double> pauseDurations = new ArrayList<>();
        int longPauseCount = 0;
        double longPauseTimeSec = 0.0;

        Instant lastTs = null;
        Integer lastState = null; // 1=playing,2=paused,3=buffering
        double lastRate = 1.0;
        double lastPos = 0.0;
        int clampedIntervalCount = 0;

        for (TrustedEvent te : timelineEvents) {
            SessionEvent row = te.row();
            Instant curTs = te.effectiveTs();
            Integer curState = row.getPlayerState();
            Double curRateObj = row.getPlaybackRate();
            Double curPosObj = row.getCurrentTimeSec();

            double curRate = (curRateObj != null ? curRateObj : lastRate);
            double curPos = (curPosObj != null ? curPosObj : lastPos);

            if (lastTs != null) {
                double rawDt = Math.max(Duration.between(lastTs, curTs).toMillis() / 1000.0, 0.0);
                double dt = Math.min(rawDt, MAX_CREDITED_INTERVAL_SEC);
                if (rawDt > MAX_CREDITED_INTERVAL_SEC) clampedIntervalCount += 1;

                if (lastState != null && lastState == 1) {
                    watchTimeSec += dt;
                    weightedRateSum += (lastRate * dt);

                    if (lastRate < 1.0) timeLt1x += dt;
                    else if (lastRate > 1.0) timeGt1x += dt;
                    else time1x += dt;

                } else if (lastState != null && lastState == 2) {
                    totalPauseDurationSec += dt;
                    pauseDurations.add(dt);
                    if (dt >= LONG_PAUSE_THRESHOLD_SEC) {
                        longPauseCount += 1;
                        longPauseTimeSec += dt;
                    }

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

                if (delta > 1.0) {
                    numSeekForward += 1;
                    totalSeekForwardSec += delta;
                    largestForwardSeekSec = Math.max(largestForwardSeekSec, delta);
                    seekDeltas.add(delta);
                } else if (delta < -1.0) {
                    double d = Math.abs(delta);
                    numSeekBackward += 1;
                    totalSeekBackwardSec += d;
                    largestBackwardSeekSec = Math.max(largestBackwardSeekSec, d);
                    seekDeltas.add(delta);
                }
            }

            lastTs = curTs;
            if (curState != null) lastState = curState;
            lastRate = curRate;
            lastPos = curPos;
        }

        watchTimeSec = Math.min(watchTimeSec, videoDurationSec);
        double videoMin = Math.max(videoDurationSec / 60.0, EPS);

        double watchTimeRatio = Math.min(watchTimeSec / videoDurationSec, 1.0);
        double completionRatio = Math.min(lastPositionSec / videoDurationSec, 1.0);
        double engagementVelocity = watchTimeSec / (sessionDurationSec + EPS);

        double pauseFreqPerMin = numPause / videoMin;
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
        double longPauseRatio = (totalPauseDurationSec > 0 ? longPauseTimeSec / (totalPauseDurationSec + EPS) : 0.0);

        double skipTimeRatio = totalSeekForwardSec / (videoDurationSec + EPS);
        double rewatchTimeRatio = totalSeekBackwardSec / (videoDurationSec + EPS);
        double rewatchToSkipRatio = (rewatchTimeRatio + EPS) / (skipTimeRatio + EPS);

        int totalSeeks = numSeekForward + numSeekBackward;
        double seekDensityPerMin = totalSeeks / videoMin;

        double avgSeekForward = (numSeekForward > 0 ? totalSeekForwardSec / numSeekForward : 0.0);
        double avgSeekBackward = (numSeekBackward > 0 ? totalSeekBackwardSec / numSeekBackward : 0.0);

        double seekForwardRatio = (totalSeeks > 0 ? (double) numSeekForward / (totalSeeks + EPS) : 0.0);
        double seekBackwardRatio = (totalSeeks > 0 ? (double) numSeekBackward / (totalSeeks + EPS) : 0.0);

        double seekJumpStd = 0.0;
        if (seekDeltas.size() >= 2) {
            double mean = seekDeltas.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double sum = 0.0;
            for (double v : seekDeltas) sum += (v - mean) * (v - mean);
            seekJumpStd = Math.sqrt(sum / seekDeltas.size());
        }

        double fastRatio = timeGt1x / (videoDurationSec + EPS);
        double slowRatio = timeLt1x / (videoDurationSec + EPS);
        double playbackSpeedVariance = fastRatio + slowRatio;
        double avgPlaybackRateWhenPlaying = (watchTimeSec > 0 ? weightedRateSum / (watchTimeSec + EPS) : 1.0);

        Set<Double> speedSet = new HashSet<>();
        for (SessionEvent e : events) {
            if (e.getPlaybackRate() != null) speedSet.add(e.getPlaybackRate());
        }
        double uniqueSpeedLevels = Math.max(speedSet.size(), 1);

        double bufferingFreqPerMin = numBuffer / videoMin;
        double playPauseRatio = numPlay / (numPause + EPS);
        double firstSeekTime = (firstSeekTimeSec != null ? firstSeekTimeSec : -1.0);
        int earlySkipFlag = (totalSeekForwardSec > 0 && lastPositionSec < 0.1 * videoDurationSec) ? 1 : 0;
        double attentionIndex = watchTimeRatio - skipTimeRatio + rewatchTimeRatio;
        int skimFlag = (fastRatio >= 0.2 && skipTimeRatio >= 0.2) ? 1 : 0;
        int deepFlag = (rewatchTimeRatio >= 0.05 || longPauseCount >= 1) ? 1 : 0;
        int timelineSuspiciousFlag = (trustedTimeline.nonMonotonicClientCount() > 0
                || trustedTimeline.driftViolationCount() > 0
                || clampedIntervalCount > 0) ? 1 : 0;

        putIfExists(f, "session_duration_sec", sessionDurationSec);
        putIfExists(f, "video_duration_sec", videoDurationSec);
        putIfExists(f, "last_position_sec", lastPositionSec);
        putIfExists(f, "completed_flag", completed ? 1.0 : 0.0);

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

        putIfExists(f, "num_seek", (double) numSeekRaw);
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
        putIfExists(f, "timeline_suspicious_flag", timelineSuspiciousFlag);
        putIfExists(f, "timeline_non_monotonic_count", trustedTimeline.nonMonotonicClientCount());
        putIfExists(f, "timeline_drift_violation_count", trustedTimeline.driftViolationCount());
        putIfExists(f, "timeline_fallback_to_server_count", trustedTimeline.fallbackToServerCount());
        putIfExists(f, "timeline_clamped_interval_count", clampedIntervalCount);

        return f;
    }

    private TrustedTimeline buildTrustedTimeline(List<SessionEvent> events) {
        List<TrustedEvent> out = new ArrayList<>();
        if (events.isEmpty()) return new TrustedTimeline(out, 0, 0, 0);

        long firstServerMs = Optional.ofNullable(events.get(0).getCreatedAtUtc())
                .orElse(Instant.now())
                .toEpochMilli();
        Long firstClientMs = null;
        Long prevNormalizedClientMs = null;
        Instant prevEffectiveTs = null;

        int nonMonotonicClientCount = 0;
        int driftViolationCount = 0;
        int fallbackToServerCount = 0;

        for (SessionEvent row : events) {
            Instant serverTs = Optional.ofNullable(row.getCreatedAtUtc()).orElse(Instant.now());
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

            Instant effectiveTs = serverTs;
            if (normalizedClientMs != null) {
                long driftMs = Math.abs(normalizedClientMs - serverTs.toEpochMilli());
                if (driftMs <= MAX_CLIENT_SERVER_DRIFT_SEC * 1000) {
                    effectiveTs = Instant.ofEpochMilli(normalizedClientMs);
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

    private record TrustedEvent(SessionEvent row, Instant effectiveTs) {}

    private record TrustedTimeline(
            List<TrustedEvent> events,
            int nonMonotonicClientCount,
            int driftViolationCount,
            int fallbackToServerCount
    ) {}
}
