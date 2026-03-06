package com.certifytube.backend.service;

import com.certifytube.backend.dto.CertificateResponse;
import com.certifytube.backend.model.Certificate;
import com.certifytube.backend.model.QuizAttempt;
import com.certifytube.backend.repository.CertificateRepository;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateService {

    private final CertificateRepository certificateRepository;

    @Value("${app.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    @Transactional
    public Certificate issueIfAbsent(Long userId, String sessionId, QuizAttempt attempt) {
        return certificateRepository.findTopByUserIdAndSessionIdOrderByCreatedAtUtcDesc(userId, sessionId)
                .orElseGet(() -> {
                    Certificate cert = certificateRepository.save(Certificate.builder()
                            .certificateId(UUID.randomUUID().toString())
                            .userId(userId)
                            .sessionId(sessionId)
                            .quizAttemptId(attempt.getId())
                            .scorePercent(attempt.getScorePercent())
                            .certificateNumber("CT-" + Instant.now().toEpochMilli() + "-" + userId)
                            .verificationToken(UUID.randomUUID().toString().replace("-", ""))
                            .pdfBytes(generatePdf(userId, sessionId, attempt.getScorePercent()))
                            .createdAtUtc(Instant.now())
                            .build());
                    log.info("Issued and saved new Certificate={} to DB for session={}", cert.getCertificateId(), sessionId);
                    return cert;
                });
    }

    @Transactional(readOnly = true)
    public CertificateResponse getOwnedCertificate(Long userId, String certificateId) {
        Certificate cert = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found"));
        if (!cert.getUserId().equals(userId)) {
            throw new AccessDeniedException("Certificate does not belong to authenticated user");
        }
        return toResponse(cert);
    }

    @Transactional(readOnly = true)
    public byte[] getOwnedCertificatePdf(Long userId, String certificateId) {
        Certificate cert = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found"));
        if (!cert.getUserId().equals(userId)) {
            throw new AccessDeniedException("Certificate does not belong to authenticated user");
        }
        return cert.getPdfBytes();
    }

    @Transactional(readOnly = true)
    public CertificateResponse verify(String verificationToken) {
        Certificate cert = certificateRepository.findByVerificationToken(verificationToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid certificate link"));
        return toResponse(cert);
    }

    private CertificateResponse toResponse(Certificate cert) {
        String link = publicBaseUrl + "/api/certificates/verify/" + cert.getVerificationToken();
        return CertificateResponse.builder()
                .certificateId(cert.getCertificateId())
                .certificateNumber(cert.getCertificateNumber())
                .sessionId(cert.getSessionId())
                .userId(cert.getUserId())
                .scorePercent(cert.getScorePercent())
                .verificationToken(cert.getVerificationToken())
                .verificationLink(link)
                .createdAtUtc(cert.getCreatedAtUtc().toString())
                .build();
    }

    private byte[] generatePdf(Long userId, String sessionId, Double scorePercent) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // Background setup
                float pageWidth = page.getMediaBox().getWidth();
                float pageHeight = page.getMediaBox().getHeight();

                // Draw decorative border
                cs.setStrokingColor(41, 128, 185);  // Professional blue
                cs.setLineWidth(3);
                cs.addRect(30, 30, pageWidth - 60, pageHeight - 60);
                cs.stroke();

                // Inner decorative border
                cs.setStrokingColor(52, 152, 219);  // Lighter blue
                cs.setLineWidth(1);
                cs.addRect(40, 40, pageWidth - 80, pageHeight - 80);
                cs.stroke();

                // Title
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD), 36);
                cs.setNonStrokingColor(41, 128, 185);
                float titleX = (pageWidth - 300) / 2;
                cs.newLineAtOffset(titleX, 700);
                cs.showText("CertifyTube");
                cs.endText();

                // Subtitle
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN), 16);
                cs.setNonStrokingColor(100, 100, 100);
                cs.newLineAtOffset((pageWidth - 220) / 2, 675);
                cs.showText("Certificate of Completion");
                cs.endText();

                // Divider line
                cs.setStrokingColor(169, 169, 169);
                cs.setLineWidth(1);
                cs.moveTo(100, 660);
                cs.lineTo(pageWidth - 100, 660);
                cs.stroke();

                // Recognition text
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN), 14);
                cs.setNonStrokingColor(0, 0, 0);
                cs.newLineAtOffset(80, 620);
                cs.showText("This certifies that");
                cs.endText();

                // User ID (large)
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD), 20);
                cs.setNonStrokingColor(41, 128, 185);
                float userNameX = (pageWidth - 150) / 2;
                cs.newLineAtOffset(userNameX, 580);
                cs.showText("User ID: " + userId);
                cs.endText();

                // Has successfully completed text
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN), 14);
                cs.setNonStrokingColor(0, 0, 0);
                cs.newLineAtOffset(80, 540);
                cs.showText("has successfully completed the quiz assessment");
                cs.newLineAtOffset(0, -20);
                cs.showText("for the assigned video content on CertifyTube.");
                cs.endText();

                // Score section
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN), 12);
                cs.setNonStrokingColor(60, 60, 60);
                cs.newLineAtOffset(100, 460);
                cs.showText("Quiz Score: " + String.format("%.1f", scorePercent) + "%");
                cs.newLineAtOffset(0, -18);
                cs.showText("Session ID: " + sessionId);
                cs.newLineAtOffset(0, -18);
                cs.showText("Issued: " + Instant.now().toString().substring(0, 10));
                cs.newLineAtOffset(0, -18);
                cs.showText("Certificate ID: " + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
                cs.endText();

                // Divider line
                cs.setStrokingColor(169, 169, 169);
                cs.setLineWidth(1);
                cs.moveTo(100, 380);
                cs.lineTo(pageWidth - 100, 380);
                cs.stroke();

                // Signature lines
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN), 10);
                cs.setNonStrokingColor(100, 100, 100);
                cs.newLineAtOffset(120, 320);
                cs.showText("_______________________");
                cs.newLineAtOffset(0, -15);
                cs.showText("Authorized Signature");
                cs.newLineAtOffset(pageWidth - 360, 15);
                cs.showText("_______________________");
                cs.newLineAtOffset(0, -15);
                cs.showText("Date");
                cs.endText();

                // Footer
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN), 9);
                cs.setNonStrokingColor(120, 120, 120);
                cs.newLineAtOffset((pageWidth - 300) / 2, 50);
                cs.showText("Verify at: certifytube.com/verify");
                cs.endText();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate certificate PDF", e);
        }
    }
}
