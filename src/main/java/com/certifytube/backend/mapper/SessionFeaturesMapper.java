package com.certifytube.backend.mapper;

import com.certifytube.backend.model.SessionFeatures;

import java.util.Map;

public final class SessionFeaturesMapper {

    private SessionFeaturesMapper() {}

    public static SessionFeatures toEntity(
            String sessionId,
            String featureVersion,
            Map<String, Object> f
    ) {
        return SessionFeatures.builder()
                .sessionId(sessionId)
                .contractVersion(featureVersion)

                // ---- Core ----
                .sessionDurationSec(d(f, "session_duration_sec"))
                .videoDurationSec(d(f, "video_duration_sec"))
                .lastPositionSec(d(f, "last_position_sec"))
                .completedFlag(d(f, "completed_flag"))

                .watchTimeSec(d(f, "watch_time_sec"))
                .watchTimeRatio(d(f, "watch_time_ratio"))
                .completionRatio(d(f, "completion_ratio"))
                .engagementVelocity(d(f, "engagement_velocity"))

                // ---- Pause ----
                .numPause(d(f, "num_pause"))
                .totalPauseDurationSec(d(f, "total_pause_duration_sec"))
                .avgPauseDurationSec(d(f, "avg_pause_duration_sec"))
                .medianPauseDurationSec(d(f, "median_pause_duration_sec"))
                .pauseFreqPerMin(d(f, "pause_freq_per_min"))
                .longPauseCount(d(f, "long_pause_count"))
                .longPauseRatio(d(f, "long_pause_ratio"))

                // ---- Seek ----
                .numSeek(d(f, "num_seek"))
                .numSeekForward(d(f, "num_seek_forward"))
                .numSeekBackward(d(f, "num_seek_backward"))
                .totalSeekForwardSec(d(f, "total_seek_forward_sec"))
                .totalSeekBackwardSec(d(f, "total_seek_backward_sec"))
                .avgSeekForwardSec(d(f, "avg_seek_forward_sec"))
                .avgSeekBackwardSec(d(f, "avg_seek_backward_sec"))
                .largestForwardSeekSec(d(f, "largest_forward_seek_sec"))
                .largestBackwardSeekSec(d(f, "largest_backward_seek_sec"))
                .seekJumpStdSec(d(f, "seek_jump_std_sec"))

                .seekForwardRatio(d(f, "seek_forward_ratio"))
                .seekBackwardRatio(d(f, "seek_backward_ratio"))
                .skipTimeRatio(d(f, "skip_time_ratio"))
                .rewatchTimeRatio(d(f, "rewatch_time_ratio"))
                .rewatchToSkipRatio(d(f, "rewatch_to_skip_ratio"))
                .seekDensityPerMin(d(f, "seek_density_per_min"))

                .firstSeekTimeSec(d(f, "first_seek_time_sec"))
                .earlySkipFlag(d(f, "early_skip_flag"))

                // ---- Speed ----
                .numRatechange(d(f, "num_ratechange"))
                .timeAtSpeedLt1xSec(d(f, "time_at_speed_lt1x_sec"))
                .timeAtSpeed1xSec(d(f, "time_at_speed_1x_sec"))
                .timeAtSpeedGt1xSec(d(f, "time_at_speed_gt1x_sec"))

                .fastRatio(d(f, "fast_ratio"))
                .slowRatio(d(f, "slow_ratio"))
                .playbackSpeedVariance(d(f, "playback_speed_variance"))
                .avgPlaybackRateWhenPlaying(d(f, "avg_playback_rate_when_playing"))
                .uniqueSpeedLevels(d(f, "unique_speed_levels"))

                // ---- Buffering ----
                .numBufferingEvents(d(f, "num_buffering_events"))
                .bufferingTimeSec(d(f, "buffering_time_sec"))
                .bufferingFreqPerMin(d(f, "buffering_freq_per_min"))

                // ---- Composite ----
                .playPauseRatio(d(f, "play_pause_ratio"))
                .attentionIndex(d(f, "attention_index"))
                .skimFlag(d(f, "skim_flag"))
                .deepFlag(d(f, "deep_flag"))

                .build();
    }

    private static Double d(Map<String, Object> f, String key) {
        Object v = f.get(key);
        if (v == null) return 0.0;
        if (v instanceof Number n) return n.doubleValue();
        return Double.parseDouble(v.toString());
    }
}
