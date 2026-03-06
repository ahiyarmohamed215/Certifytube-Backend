package com.certifytube.backend.service;

import com.certifytube.backend.dto.DashboardResponse;
import com.certifytube.backend.dto.DashboardVideoItem;
import com.certifytube.backend.model.Certificate;
import com.certifytube.backend.model.EngagementResult;
import com.certifytube.backend.model.Session;
import com.certifytube.backend.model.YouTubeVideoCache;
import com.certifytube.backend.repository.CertificateRepository;
import com.certifytube.backend.repository.EngagementResultRepository;
import com.certifytube.backend.repository.SessionRepository;
import com.certifytube.backend.repository.YouTubeVideoCacheRepository;
import com.certifytube.backend.util.StemCategoryUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SessionRepository sessionRepository;
    private final EngagementResultRepository engagementResultRepository;
    private final CertificateRepository certificateRepository;
    private final YouTubeVideoCacheRepository videoCacheRepository;

    /**
     * Get dashboard data, optionally filtered by status.
     *
     * @param userId   the authenticated user
     * @param statuses if null → all statuses; otherwise only matching statuses
     */
    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Long userId, Set<String> statuses) {
        String userIdStr = String.valueOf(userId);

        List<Session> sessions;
        if (statuses != null && !statuses.isEmpty()) {
            sessions = sessionRepository.findByUserIdAndStatusInOrderByCreatedAtUtcDesc(userIdStr, statuses);
        } else {
            sessions = sessionRepository.findByUserIdOrderByCreatedAtUtcDesc(userIdStr);
        }

        List<DashboardVideoItem> active = new ArrayList<>();
        List<DashboardVideoItem> completed = new ArrayList<>();
        List<DashboardVideoItem> quizPending = new ArrayList<>();
        List<DashboardVideoItem> certified = new ArrayList<>();

        for (Session session : sessions) {
            DashboardVideoItem item = buildItem(session, userId);

            switch (session.getStatus() != null ? session.getStatus() : "ACTIVE") {
                case "CERTIFIED" -> certified.add(item);
                case "QUIZ_PENDING" -> quizPending.add(item);
                case "COMPLETED" -> completed.add(item);
                default -> active.add(item);
            }
        }

        return DashboardResponse.builder()
                .activeVideos(active)
                .completedVideos(completed)
                .quizPendingVideos(quizPending)
                .certifiedVideos(certified)
                .build();
    }

    private DashboardVideoItem buildItem(Session session, Long userId) {
        // Get thumbnail + STEM from YouTube video cache
        String thumbnailUrl = "";
        boolean stemEligible = false;
        Optional<YouTubeVideoCache> videoCache = videoCacheRepository.findByVideoId(session.getVideoId());
        if (videoCache.isPresent()) {
            thumbnailUrl = videoCache.get().getThumbnailUrl() != null ? videoCache.get().getThumbnailUrl() : "";
            stemEligible = StemCategoryUtil.isStemCategory(videoCache.get().getCategoryId());
        }

        // Get engagement score
        Double engagementScore = null;
        Optional<EngagementResult> engagement = engagementResultRepository
                .findTopBySessionIdOrderByCreatedAtUtcDesc(session.getSessionId());
        if (engagement.isPresent()) {
            engagementScore = engagement.get().getEngagementScore();
        }

        // Get certificate ID
        String certificateId = null;
        Optional<Certificate> cert = certificateRepository
                .findTopByUserIdAndSessionIdOrderByCreatedAtUtcDesc(userId, session.getSessionId());
        if (cert.isPresent()) {
            certificateId = cert.get().getCertificateId();
        }

        // Compute progress %
        double progressPercent = 0.0;
        if (session.getLastPositionSec() != null && session.getVideoDurationSec() != null
                && session.getVideoDurationSec() > 0) {
            progressPercent = Math.min(
                    (session.getLastPositionSec() / session.getVideoDurationSec()) * 100.0, 100.0);
        }

        return DashboardVideoItem.builder()
                .sessionId(session.getSessionId())
                .videoId(session.getVideoId())
                .videoTitle(session.getVideoTitle())
                .thumbnailUrl(thumbnailUrl)
                .lastPositionSec(session.getLastPositionSec())
                .videoDurationSec(session.getVideoDurationSec())
                .progressPercent(progressPercent)
                .status(session.getStatus() != null ? session.getStatus() : "ACTIVE")
                .stemEligible(stemEligible)
                .engagementScore(engagementScore)
                .certificateId(certificateId)
                .createdAt(session.getCreatedAtUtc() != null ? session.getCreatedAtUtc().toString() : null)
                .build();
    }
}
