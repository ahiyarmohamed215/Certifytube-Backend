package com.certifytube.backend.service;

import com.certifytube.backend.dto.CertificateResponse;
import com.certifytube.backend.model.Certificate;
import com.certifytube.backend.model.EngagementResult;
import com.certifytube.backend.model.QuizAttempt;
import com.certifytube.backend.model.Session;
import com.certifytube.backend.model.UserAccount;
import com.certifytube.backend.repository.CertificateRepository;
import com.certifytube.backend.repository.EngagementResultRepository;
import com.certifytube.backend.repository.SessionRepository;
import com.certifytube.backend.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificateServiceTest {

    @Mock
    private CertificateRepository certificateRepository;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private EngagementResultRepository engagementResultRepository;

    @InjectMocks
    private CertificateService certificateService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(certificateService, "publicBaseUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(certificateService, "engagementThresholdConfig", 0.85d);
        ReflectionTestUtils.setField(certificateService, "quizThresholdConfig", 80d);
    }

    @Test
    void issueIfAbsentShouldUseUserNameAsLearnerNameSnapshot() {
        Long userId = 7L;
        String sessionId = "session-1";

        QuizAttempt attempt = new QuizAttempt();
        attempt.setId(21L);
        attempt.setScorePercent(91.0);

        UserAccount user = new UserAccount();
        user.setId(userId);
        user.setEmail("john@example.com");
        user.setName("John Doe");

        Session session = new Session();
        session.setSessionId(sessionId);
        session.setVideoTitle("Data Structures Full Course");
        session.setVideoId("abc123xyz12");
        session.setVideoDurationSec(3600.0);

        EngagementResult engagementResult = new EngagementResult();
        engagementResult.setEngagementScore(0.92);

        when(certificateRepository.findTopByUserIdAndSessionIdOrderByCreatedAtUtcDesc(userId, sessionId))
                .thenReturn(Optional.empty());
        when(userAccountRepository.findById(userId)).thenReturn(Optional.of(user));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(engagementResultRepository.findTopBySessionIdOrderByCreatedAtUtcDesc(sessionId))
                .thenReturn(Optional.of(engagementResult));
        when(certificateRepository.save(any(Certificate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Certificate issuedCertificate = certificateService.issueIfAbsent(userId, sessionId, attempt);

        assertEquals("John Doe", issuedCertificate.getLearnerName());

        ArgumentCaptor<Certificate> certCaptor = ArgumentCaptor.forClass(Certificate.class);
        verify(certificateRepository, atLeast(2)).save(certCaptor.capture());
        List<Certificate> savedCertificates = certCaptor.getAllValues();
        assertEquals("John Doe", savedCertificates.getFirst().getLearnerName());
    }

    @Test
    void verifyShouldReturnLearnerNameFromCertificateData() {
        String token = "verifytoken123";
        Certificate cert = new Certificate();
        cert.setCertificateId("cert-1");
        cert.setCertificateNumber("CT-1");
        cert.setSessionId("session-1");
        cert.setUserId(1L);
        cert.setScorePercent(90.0);
        cert.setLearnerName("John Doe");
        cert.setVideoTitle("Algorithms Explained");
        cert.setVideoId("abc123xyz12");
        cert.setVideoDurationSec(900.0);
        cert.setFinalEngagementScore(0.9);
        cert.setFinalQuizScore(0.9);
        cert.setEngagementThreshold(0.85);
        cert.setQuizThreshold(0.8);
        cert.setVerificationToken(token);
        cert.setStatus("ACTIVE");
        cert.setCreatedAtUtc(LocalDateTime.parse("2026-03-10T12:00:00"));

        when(certificateRepository.findByVerificationToken(token)).thenReturn(Optional.of(cert));

        CertificateResponse response = certificateService.verify(token);

        assertEquals("John Doe", response.getLearnerName());
        assertEquals("cert-1", response.getCertificateId());
    }
}
