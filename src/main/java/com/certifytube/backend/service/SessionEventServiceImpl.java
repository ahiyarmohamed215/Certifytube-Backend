package com.certifytube.backend.service;

import com.certifytube.backend.dto.EventBatchError;
import com.certifytube.backend.dto.EventBatchRequest;
import com.certifytube.backend.dto.EventBatchResponse;
import com.certifytube.backend.dto.SystemFlowDto;
import com.certifytube.backend.model.Session;
import com.certifytube.backend.model.UserAccount;
import com.certifytube.backend.model.SessionEvent;
import com.certifytube.backend.repository.SessionRepository;
import com.certifytube.backend.repository.SessionEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.certifytube.backend.util.SystemFlowUtil.data;
import static com.certifytube.backend.util.SystemFlowUtil.flow;
import static com.certifytube.backend.util.SystemFlowUtil.step;

@Slf4j
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
                    .systemFlow(buildEmptyBatchFlow())
                    .build();
        }
        UserAccount user = authenticatedUserService.currentUser();
        String userIdFromJwt = String.valueOf(user.getId());
        Map<String, Session> sessionCache = new HashMap<>();

        List<SessionEvent> entities = new ArrayList<>();

        // Track latest position per session to update progress
        Map<String, Double> latestPositionPerSession = new HashMap<>();
        Map<String, Double> latestDurationPerSession = new HashMap<>();

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
                    .createdAtUtc(LocalDateTime.now())
                    .build();

            entities.add(entity);

            // Track latest position for progress update
            if (req.getCurrentTimeSec() != null) {
                Double prev = latestPositionPerSession.get(req.getSessionId());
                if (prev == null || req.getCurrentTimeSec() > prev) {
                    latestPositionPerSession.put(req.getSessionId(), req.getCurrentTimeSec());
                }
            }
            if (req.getVideoDurationSec() != null && req.getVideoDurationSec() > 0) {
                latestDurationPerSession.put(req.getSessionId(), req.getVideoDurationSec());
            }
        }

        if (!entities.isEmpty()) {
            eventRepository.saveAll(entities);
            log.debug("session.events.saved count={}", entities.size());
        }

        // Update session progress (lastPositionSec + videoDurationSec)
        int updatedSessions = 0;
        for (Map.Entry<String, Double> entry : latestPositionPerSession.entrySet()) {
            Session session = sessionCache.get(entry.getKey());
            if (session != null) {
                session.setLastPositionSec(entry.getValue());
                Double duration = latestDurationPerSession.get(entry.getKey());
                if (duration != null) {
                    session.setVideoDurationSec(duration);
                }
                sessionRepository.save(session);
                updatedSessions++;
            }
        }
        if (updatedSessions > 0) {
            log.debug("session.progress.updated sessions={}", updatedSessions);
        }
        log.info("session.events.batch.processed received={} saved={} rejected={} updatedSessions={}",
                events.size(), entities.size(), errors.size(), updatedSessions);

        return EventBatchResponse.builder()
                .saved(entities.size())
                .rejected(errors.size())
                .errors(errors)
                .systemFlow(buildBatchFlow(events.size(), entities, errors.size(), updatedSessions))
                .build();
    }

    private SystemFlowDto buildEmptyBatchFlow() {
        return flow("event-batch", List.of(
                step(
                        "validate-events",
                        "skipped",
                        "No session events were received in the batch.",
                        data(
                                "receivedCount", 0,
                                "savedCount", 0,
                                "rejectedCount", 0))));
    }

    private SystemFlowDto buildBatchFlow(
            int receivedCount,
            List<SessionEvent> entities,
            int rejectedCount,
            int updatedSessions) {

        return flow("event-batch", List.of(
                step(
                        "validate-events",
                        "completed",
                        "Validated the incoming watch-event payload from the frontend.",
                        data(
                                "receivedCount", receivedCount,
                                "savedCount", entities.size(),
                                "rejectedCount", rejectedCount)),
                step(
                        "persist-events",
                        "completed",
                        "Stored accepted watch events in the database.",
                        data(
                                "savedCount", entities.size(),
                                "sampleEventTypes", sampleEventTypes(entities))),
                step(
                        "update-session-progress",
                        "completed",
                        "Updated session progress using the latest watch positions.",
                        data("updatedSessions", updatedSessions))));
    }

    private List<String> sampleEventTypes(List<SessionEvent> entities) {
        return entities.stream()
                .map(SessionEvent::getEventType)
                .filter(type -> type != null && !type.isBlank())
                .distinct()
                .limit(5)
                .toList();
    }
}
