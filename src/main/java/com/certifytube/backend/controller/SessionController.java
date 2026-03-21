package com.certifytube.backend.controller;

import com.certifytube.backend.dto.StartSessionRequest;
import com.certifytube.backend.dto.StartSessionResponse;
import com.certifytube.backend.dto.EndSessionResponse;
import com.certifytube.backend.model.Session;
import com.certifytube.backend.model.UserAccount;
import com.certifytube.backend.model.YouTubeVideoCache;
import com.certifytube.backend.repository.YouTubeVideoCacheRepository;
import com.certifytube.backend.service.AuthenticatedUserService;
import com.certifytube.backend.service.SessionService;
import com.certifytube.backend.util.StemCategoryUtil;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
public class SessionController {

    private final SessionService sessionService;
    private final AuthenticatedUserService authenticatedUserService;
    private final YouTubeVideoCacheRepository videoCacheRepository;

    public SessionController(SessionService sessionService,
            AuthenticatedUserService authenticatedUserService,
            YouTubeVideoCacheRepository videoCacheRepository) {
        this.sessionService = sessionService;
        this.authenticatedUserService = authenticatedUserService;
        this.videoCacheRepository = videoCacheRepository;
    }

    @PostMapping("/api/sessions/start")
    public StartSessionResponse startSession(@Valid @RequestBody StartSessionRequest req) {
        UserAccount user = authenticatedUserService.currentUser();
        String userId = String.valueOf(user.getId());
        
        log.info("User {} starting session for videoId={}", userId, req.getVideoId());

        // Check for resume (existing open session)
        Session s = sessionService.startSession(userId, req.getVideoId(), req.getVideoTitle());
        boolean resumed = s.getCreatedAtUtc() != null
                && s.getEndedAtUtc() == null
                && s.getLastPositionSec() != null
                && s.getLastPositionSec() > 0;

        // Look up STEM eligibility from YouTube video cache
        boolean stemEligible = false;
        String stemMessage = null;
        YouTubeVideoCache videoCache = videoCacheRepository.findByVideoId(req.getVideoId()).orElse(null);
        if (videoCache != null) {
            stemEligible = StemCategoryUtil.isStemVideo(videoCache);
        }
        if (!stemEligible) {
            stemMessage = "Only STEM-based skill videos (Science, Technology, Engineering, Mathematics) "
                    + "are eligible for engagement analysis, quiz, and certification. "
                    + "You can still watch this video but no certificate will be issued.";
        }

        log.info("Session started => sessionId={}, resumed={}, stemEligible={}", s.getSessionId(), resumed, stemEligible);

        return StartSessionResponse.builder()
                .sessionId(s.getSessionId())
                .resumed(resumed)
                .lastPositionSec(s.getLastPositionSec())
                .videoDurationSec(s.getVideoDurationSec())
                .stemEligible(stemEligible)
                .stemMessage(stemMessage)
                .build();
    }

    @PostMapping("/api/sessions/end")
    public EndSessionResponse endSession(@RequestParam String sessionId) {
        UserAccount user = authenticatedUserService.currentUser();
        log.info("User {} ending session {}", user.getId(), sessionId);
        
        Session session = sessionService.getById(sessionId);
        if (!String.valueOf(user.getId()).equals(session.getUserId())) {
            throw new AccessDeniedException("Session does not belong to authenticated user");
        }
        sessionService.endSession(sessionId);
        
        log.info("Session {} ended successfully", sessionId);
        return new EndSessionResponse(true);
    }

    @DeleteMapping("/api/sessions/{sessionId}")
    public ResponseEntity<java.util.Map<String, String>> deleteSession(@PathVariable String sessionId) {
        UserAccount user = authenticatedUserService.currentUser();
        log.info("User {} requesting to delete session {}", user.getId(), sessionId);
        
        sessionService.deleteSession(sessionId, String.valueOf(user.getId()));
        
        log.info("Session {} deleted successfully", sessionId);
        return ResponseEntity.ok(java.util.Map.of("message", "Session deleted successfully"));
    }
}
