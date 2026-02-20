package com.certifytube.backend.service;

import com.certifytube.backend.dto.EventBatchError;
import com.certifytube.backend.dto.EventBatchRequest;
import com.certifytube.backend.dto.EventBatchResponse;
import com.certifytube.backend.model.Session;
import com.certifytube.backend.model.UserAccount;
import com.certifytube.backend.model.SessionEvent;
import com.certifytube.backend.repository.SessionRepository;
import com.certifytube.backend.repository.SessionEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionEventServiceImpl implements SessionEventService {

    private final SessionEventRepository eventRepository;
    private final AuthenticatedUserService authenticatedUserService;
    private final SessionRepository sessionRepository;

    @Override
    @Transactional
    public EventBatchResponse saveBatch(List<EventBatchRequest> events) {

        List<EventBatchError> errors = new ArrayList<>();

        if (events == null || events.isEmpty()) {
            return EventBatchResponse.builder()
                    .saved(0)
                    .rejected(0)
                    .errors(errors)
                    .build();
        }
        UserAccount user = authenticatedUserService.currentUser();
        String userIdFromJwt = String.valueOf(user.getId());
        Map<String, Session> sessionCache = new HashMap<>();

        List<SessionEvent> entities = new ArrayList<>();

        for (int i = 0; i < events.size(); i++) {
            EventBatchRequest req = events.get(i);

            if (req.getEventType() == null || req.getEventType().isBlank()) {
                errors.add(EventBatchError.builder()
                        .index(i)
                        .message("eventType is required")
                        .build());
                continue;
            }

            if (req.getSessionId() == null || req.getSessionId().isBlank()) {
                errors.add(EventBatchError.builder()
                        .index(i)
                        .message("sessionId is required")
                        .build());
                continue;
            }

            Session session = sessionCache.get(req.getSessionId());
            if (session == null) {
                session = sessionRepository.findById(req.getSessionId()).orElse(null);
                if (session == null) {
                    errors.add(EventBatchError.builder()
                            .index(i)
                            .message("Session not found")
                            .build());
                    continue;
                }
                sessionCache.put(req.getSessionId(), session);
            }

            if (!userIdFromJwt.equals(session.getUserId())) {
                throw new AccessDeniedException("Session does not belong to authenticated user");
            }

            if (session.getEndedAtUtc() != null) {
                errors.add(EventBatchError.builder()
                        .index(i)
                        .message("Session already ended")
                        .build());
                continue;
            }

            SessionEvent entity = SessionEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .sessionId(req.getSessionId())
                    .userId(userIdFromJwt)
                    .videoId(session.getVideoId())
                    .videoTitle(session.getVideoTitle())
                    .eventType(req.getEventType())
                    .playerState(req.getPlayerState())
                    .playbackRate(req.getPlaybackRate())
                    .currentTimeSec(req.getCurrentTimeSec())
                    .videoDurationSec(req.getVideoDurationSec())
                    .clientCreatedAtLocal(req.getClientCreatedAtLocal())
                    .clientTzOffsetMin(req.getClientTzOffsetMin())
                    .clientEventMs(req.getClientEventMs())
                    .seekFromSec(req.getSeekFromSec())
                    .seekToSec(req.getSeekToSec())
                    .createdAtUtc(Instant.now())
                    .build();

            entities.add(entity);
        }

        eventRepository.saveAll(entities);
        return EventBatchResponse.builder()
                .saved(entities.size())
                .rejected(errors.size())
                .errors(errors)
                .build();
    }
}
