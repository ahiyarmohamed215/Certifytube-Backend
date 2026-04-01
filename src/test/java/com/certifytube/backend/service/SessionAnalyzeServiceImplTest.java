package com.certifytube.backend.service;

import com.certifytube.backend.client.MlServiceClient;
import com.certifytube.backend.dto.SessionAnalyzeResponse;
import com.certifytube.backend.model.EngagementResult;
import com.certifytube.backend.model.Session;
import com.certifytube.backend.model.SessionEvent;
import com.certifytube.backend.model.UserAccount;
import com.certifytube.backend.model.YouTubeVideoCache;
import com.certifytube.backend.repository.EngagementResultRepository;
import com.certifytube.backend.repository.SessionEventRepository;
import com.certifytube.backend.repository.SessionRepository;
import com.certifytube.backend.repository.YouTubeVideoCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionAnalyzeServiceImplTest {

    @Mock
    private EngagementResultRepository engagementResultRepository;
    @Mock
    private SessionService sessionService;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private SessionEventRepository sessionEventRepository;
    @Mock
    private AuthenticatedUserService authenticatedUserService;
    @Mock
    private MlServiceClient mlServiceClient;
    @Mock
    private YouTubeVideoCacheRepository videoCacheRepository;

    @InjectMocks
    private SessionAnalyzeServiceImpl sessionAnalyzeService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sessionAnalyzeService, "featureVersion", "v1.0");
        ReflectionTestUtils.setField(sessionAnalyzeService, "engagementThreshold", 0.85d);
        ReflectionTestUtils.setField(sessionAnalyzeService, "defaultModel", "xgboost");
    }

    @Test
    void analyzeSessionShouldForwardMlExplanationUsingBackendThreshold() {
        String sessionId = "session-1";

        UserAccount user = new UserAccount();
        user.setId(7L);

        Session session = new Session();
        session.setSessionId(sessionId);
        session.setUserId("7");
        session.setVideoId("video-1");
        session.setVideoTitle("Java Tutorial");
        session.setCreatedAtUtc(LocalDateTime.now().minusMinutes(10));
        session.setEndedAtUtc(LocalDateTime.now().minusMinutes(1));
        session.setStatus("COMPLETED");

        SessionEvent event = SessionEvent.builder()
                .eventId("evt-1")
                .sessionId(sessionId)
                .userId("7")
                .videoId("video-1")
                .videoTitle("Java Tutorial")
                .eventType("play")
                .playerState(1)
                .playbackRate(1.0)
                .currentTimeSec(0.0)
                .videoDurationSec(600.0)
                .createdAtUtc(LocalDateTime.now().minusMinutes(9))
                .build();

        YouTubeVideoCache video = YouTubeVideoCache.builder()
                .videoId("video-1")
                .title("Java Tutorial")
                .channelTitle("Channel")
                .iframeUrl("https://youtube.com/embed/video-1")
                .categoryId("27")
                .updatedAtUtc(LocalDateTime.now())
                .build();

        when(authenticatedUserService.currentUser()).thenReturn(user);
        when(sessionService.getById(sessionId)).thenReturn(session);
        when(videoCacheRepository.findByVideoId("video-1")).thenReturn(Optional.of(video));
        when(engagementResultRepository.findTopBySessionIdOrderByCreatedAtUtcDesc(sessionId))
                .thenReturn(Optional.empty());
        when(sessionEventRepository.findBySessionIdOrderByCreatedAtUtcAsc(sessionId))
                .thenReturn(List.of(event));
        when(mlServiceClient.analyzeEngagement(eq(sessionId), eq("v1.0"), anyList(), eq("xgboost"), eq(0.85d)))
                .thenReturn(Map.of(
                        "engagement_score", 0.91d,
                        "explanation", "Congratulations, you are engaged with a score of 91%. Main reasons behind this score: coverage stayed strong across most of the lesson.",
                        "shap_top_positive", List.of(),
                        "shap_top_negative", List.of()));
        when(engagementResultRepository.save(any(EngagementResult.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SessionAnalyzeResponse response = sessionAnalyzeService.analyzeSession(sessionId, "xgboost");

        assertEquals("ENGAGED", response.getStatus());
        assertEquals(
                "Congratulations, you are engaged with a score of 91%. Main reasons behind this score: coverage stayed strong across most of the lesson.",
                response.getExplanation());
        assertEquals("QUIZ_PENDING", session.getStatus());

        verify(mlServiceClient).analyzeEngagement(eq(sessionId), eq("v1.0"), anyList(), eq("xgboost"), eq(0.85d));
    }
}
