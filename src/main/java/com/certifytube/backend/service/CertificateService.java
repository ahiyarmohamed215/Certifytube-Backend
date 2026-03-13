package com.certifytube.backend.service;

import com.certifytube.backend.dto.CertificateResponse;
import com.certifytube.backend.model.Certificate;
import com.certifytube.backend.model.QuizAttempt;
import com.certifytube.backend.model.EngagementResult;
import com.certifytube.backend.model.Session;
import com.certifytube.backend.model.UserAccount;
import com.certifytube.backend.repository.CertificateRepository;
import com.certifytube.backend.repository.EngagementResultRepository;
import com.certifytube.backend.repository.SessionRepository;
import com.certifytube.backend.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateService {

    private final CertificateRepository certificateRepository;
    private final UserAccountRepository userAccountRepository;
    private final SessionRepository sessionRepository;
    private final EngagementResultRepository engagementResultRepository;

    @Value("${app.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    @Value("${quiz.min-engagement-score:0.85}")
    private double engagementThresholdConfig;

    @Value("${quiz.pass-score:80}")
    private double quizThresholdConfig;

    /* ─────────────────── Issue ─────────────────── */

    @Transactional
    public Certificate issueIfAbsent(Long userId, String sessionId, QuizAttempt attempt) {
        return certificateRepository.findTopByUserIdAndSessionIdOrderByCreatedAtUtcDesc(userId, sessionId)
                .orElseGet(() -> {
                    UserAccount user = userAccountRepository.findById(userId)
                            .orElseThrow(() -> new IllegalArgumentException("User not found"));
                    Session session = sessionRepository.findById(sessionId)
                            .orElseThrow(() -> new IllegalArgumentException("Session not found"));
                    EngagementResult engagement = engagementResultRepository.findTopBySessionIdOrderByCreatedAtUtcDesc(sessionId)
                            .orElseThrow(() -> new IllegalArgumentException("Engagement result not found"));

                    String learnerName = normalizeLearnerName(user.getName());

                    Certificate cert = certificateRepository.save(Certificate.builder()
                            .certificateId(UUID.randomUUID().toString())
                            .userId(userId)
                            .sessionId(sessionId)
                            .quizAttemptId(attempt.getId())
                            .scorePercent(attempt.getScorePercent())
                            .certificateNumber("CT-" + Instant.now().toEpochMilli() + "-" + userId)
                            .verificationToken(UUID.randomUUID().toString().replace("-", ""))
                            .finalEngagementScore(engagement.getEngagementScore())
                            .finalQuizScore(attempt.getScorePercent() / 100.0) // 0-1 range
                            .learnerName(learnerName)
                            .videoTitle(session.getVideoTitle())
                            .videoId(session.getVideoId())
                            .videoDurationSec(session.getVideoDurationSec())
                            .engagementThreshold(engagementThresholdConfig)
                            .quizThreshold(quizThresholdConfig / 100.0)  // store as 0-1
                            .status("ACTIVE")
                            .createdAtUtc(Instant.now())
                            // PDF generated after first save so entity has an ID
                            .pdfBytes(new byte[0]) 
                            .build());
                    
                    byte[] pdfBytes = generatePdf(cert);
                    cert.setPdfBytes(pdfBytes);
                    certificateRepository.save(cert);

                    log.info("Issued and saved new Certificate={} to DB for session={}", cert.getCertificateId(), sessionId);
                    return cert;
                });
    }

    /* ─────────────────── Read ─────────────────── */

    @Transactional(readOnly = true)
    public CertificateResponse getOwnedCertificate(Long userId, String certificateId) {
        Certificate cert = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found"));
        if (!cert.getUserId().equals(userId)) {
            throw new AccessDeniedException("Certificate does not belong to authenticated user");
        }
        return toResponse(cert, false);
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

    @Transactional
    public void deleteOwnedCertificate(Long userId, String certificateId) {
        Certificate cert = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found"));
        if (!cert.getUserId().equals(userId)) {
            throw new AccessDeniedException("Certificate does not belong to authenticated user");
        }
        certificateRepository.delete(cert);
        log.info("User {} deleted certificate {}", userId, certificateId);
    }

    /* ─────────────────── Verify (public) ─────────────────── */

    @Transactional(readOnly = true)
    public CertificateResponse verify(String verificationToken) {
        Certificate cert = certificateRepository.findByVerificationToken(verificationToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid certificate link"));
        return toResponse(cert, true);
    }

    /* ─────────────────── Admin Revoke ─────────────────── */

    @Transactional
    public void revoke(String certificateId) {
        Certificate cert = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found"));
        if ("REVOKED".equals(cert.getStatus())) {
            throw new IllegalStateException("Certificate is already revoked");
        }
        cert.setStatus("REVOKED");
        certificateRepository.save(cert);
        log.info("Admin revoked Certificate={}", certificateId);
    }

    /* ─────────────────── DTO mapping ─────────────────── */

    private CertificateResponse toResponse(Certificate cert, boolean isPublic) {
        String link = publicBaseUrl + "/api/certificates/verify/" + cert.getVerificationToken();
        String status = cert.getStatus() != null ? cert.getStatus() : "ACTIVE";
        boolean valid = "ACTIVE".equals(status);

        return CertificateResponse.builder()
                .certificateId(cert.getCertificateId())
                .certificateNumber(cert.getCertificateNumber())
                .sessionId(cert.getSessionId())
                .userId(isPublic ? null : cert.getUserId())
                .scorePercent(cert.getScorePercent())
                .learnerName(cert.getLearnerName())
                .videoTitle(cert.getVideoTitle())
                .videoId(cert.getVideoId())
                .videoUrl("https://www.youtube.com/watch?v=" + cert.getVideoId())
                .videoDuration(formatDuration(cert.getVideoDurationSec()))
                .engagementScore(cert.getFinalEngagementScore())
                .quizScore(cert.getFinalQuizScore())
                .engagementThreshold(cert.getEngagementThreshold())
                .quizThreshold(cert.getQuizThreshold())
                .platformName("CertifyTube")
                .platformAttribution("Verification Layer 1 & 2")
                .status(status)
                .valid(valid)
                .verificationToken(cert.getVerificationToken())
                .verificationLink(link)
                .createdAtUtc(cert.getCreatedAtUtc().toString())
                .build();
    }

    /* ─────────────────── Helpers ─────────────────── */

    private String formatDuration(Double seconds) {
        if (seconds == null || seconds <= 0) return "N/A";
        long totalSec = Math.round(seconds);
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        if (h > 0) return String.format("%dh %02dm %02ds", h, m, s);
        if (m > 0) return String.format("%dm %02ds", m, s);
        return String.format("%ds", s);
    }

    private String normalizeLearnerName(String name) {
        if (name == null) {
            return "Learner";
        }
        String trimmed = name.trim();
        return trimmed.isEmpty() ? "Learner" : trimmed;
    }

    private String sanitizePdfText(PDFont font, String text) {
        String value = text == null ? "N/A" : text.replace('\n', ' ').replace('\r', ' ').trim();
        if (value.isEmpty()) {
            return "N/A";
        }

        StringBuilder safe = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            try {
                font.encode(String.valueOf(ch));
                safe.append(ch);
            } catch (Exception ignored) {
                safe.append('?');
            }
        }
        return safe.toString();
    }

    /* ═══════════════════ PDF Generation ═══════════════════ */

    private byte[] generatePdf(Certificate cert) {
        try (PDDocument doc = new PDDocument()) {
            // Landscape A4
            PDPage page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float pageWidth = page.getMediaBox().getWidth();
                float pageHeight = page.getMediaBox().getHeight();

                // Fonts
                PDType1Font fontBold   = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font fontNormal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                PDType1Font fontItalic = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

                // ──── 1. Background / Borders ────
                cs.setStrokingColor(80 / 255f, 80 / 255f, 80 / 255f);
                cs.setLineWidth(40);
                cs.addRect(20, 20, pageWidth - 40, pageHeight - 40);
                cs.stroke();

                cs.setStrokingColor(230 / 255f, 230 / 255f, 230 / 255f);
                cs.setLineWidth(2);
                cs.addRect(45, 45, pageWidth - 90, pageHeight - 90);
                cs.stroke();

                // ──── 2. Header ────
                cs.beginText();
                cs.setFont(fontBold, 32);
                cs.setNonStrokingColor(40 / 255f, 40 / 255f, 40 / 255f);
                cs.newLineAtOffset(80, pageHeight - 120);
                cs.showText("Certificate of Completion");
                cs.endText();

                // ──── 3. Proudly presented to ────
                cs.beginText();
                cs.setFont(fontNormal, 14);
                cs.setNonStrokingColor(120 / 255f, 130 / 255f, 140 / 255f);
                cs.newLineAtOffset(80, pageHeight - 170);
                cs.showText("Proudly presented to");
                cs.endText();

                // ──── 4. Learner Name ────
                cs.beginText();
                cs.setFont(fontBold, 48);
                cs.setNonStrokingColor(30 / 255f, 30 / 255f, 30 / 255f);
                cs.newLineAtOffset(80, pageHeight - 230);
                String name = cert.getLearnerName() != null ? cert.getLearnerName() : "Learner";
                cs.showText(sanitizePdfText(fontBold, name));
                cs.endText();

                // ──── 5. Course / Video Title ────
                cs.beginText();
                cs.setFont(fontNormal, 14);
                cs.setNonStrokingColor(60 / 255f, 60 / 255f, 60 / 255f);
                cs.newLineAtOffset(80, pageHeight - 280);
                cs.showText("Has successfully completed the assessment for:");
                cs.endText();

                cs.beginText();
                cs.setFont(fontBold, 14);
                cs.setNonStrokingColor(41 / 255f, 128 / 255f, 185 / 255f);
                cs.newLineAtOffset(80, pageHeight - 300);
                String videoTitle = cert.getVideoTitle() != null ? cert.getVideoTitle().trim() : "Untitled Video";
                if (videoTitle.length() > 55) videoTitle = videoTitle.substring(0, 52) + "...";
                cs.showText(sanitizePdfText(fontBold, videoTitle));
                cs.endText();

                // ──── 6. Video Details (Duration + YouTube Link) ────
                cs.beginText();
                cs.setFont(fontNormal, 10);
                cs.setNonStrokingColor(100 / 255f, 100 / 255f, 100 / 255f);
                cs.newLineAtOffset(80, pageHeight - 320);
                String duration = formatDuration(cert.getVideoDurationSec());
                String videoId = cert.getVideoId() != null ? cert.getVideoId() : "N/A";
                cs.showText(sanitizePdfText(fontNormal, "Duration: " + duration + "   |   https://youtube.com/watch?v=" + videoId));
                cs.endText();

                // ──── 7. Meta Info Pill (Certificate ID + Date) ────
                cs.setNonStrokingColor(245 / 255f, 248 / 255f, 255 / 255f);
                cs.addRect(80, pageHeight - 370, 450, 30);
                cs.fill();

                cs.beginText();
                cs.setFont(fontBold, 10);
                cs.setNonStrokingColor(80 / 255f, 80 / 255f, 80 / 255f);
                cs.newLineAtOffset(90, pageHeight - 360);
                String certificateIdShort = cert.getCertificateId() == null ? "N/A" : cert.getCertificateId();
                if (certificateIdShort.length() > 8) {
                    certificateIdShort = certificateIdShort.substring(0, 8);
                }
                cs.showText("Certificate ID: " + sanitizePdfText(fontBold, certificateIdShort.toUpperCase()));
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy").withZone(ZoneId.of("UTC"));
                String dateStr = formatter.format(cert.getCreatedAtUtc());
                cs.newLineAtOffset(220, 0);
                cs.showText("Issued: " + dateStr);
                cs.endText();

                // ──── 8. Scores & Thresholds Block ────
                float scoresY = pageHeight - 420;

                // Engagement score pill
                cs.setNonStrokingColor(39 / 255f, 174 / 255f, 96 / 255f);  // green
                cs.addRect(80, scoresY, 130, 28);
                cs.fill();
                cs.beginText();
                cs.setFont(fontBold, 11);
                cs.setNonStrokingColor(1f, 1f, 1f);
                cs.newLineAtOffset(88, scoresY + 9);
                double engScore = cert.getFinalEngagementScore() != null ? cert.getFinalEngagementScore() * 100.0 : 0.0;
                cs.showText("Engagement: " + String.format("%.0f", engScore) + "%");
                cs.endText();

                // Quiz score pill
                cs.setNonStrokingColor(41 / 255f, 128 / 255f, 185 / 255f);  // blue
                cs.addRect(220, scoresY, 115, 28);
                cs.fill();
                cs.beginText();
                cs.setFont(fontBold, 11);
                cs.setNonStrokingColor(1f, 1f, 1f);
                cs.newLineAtOffset(228, scoresY + 9);
                double quizScore = cert.getFinalQuizScore() != null ? cert.getFinalQuizScore() * 100.0 : 0.0;
                cs.showText("Quiz: " + String.format("%.0f", quizScore) + "%");
                cs.endText();

                // Thresholds text
                cs.beginText();
                cs.setFont(fontItalic, 9);
                cs.setNonStrokingColor(130 / 255f, 130 / 255f, 130 / 255f);
                cs.newLineAtOffset(80, scoresY - 18);
                double engThreshold = cert.getEngagementThreshold() != null ? cert.getEngagementThreshold() * 100.0 : 85.0;
                double qThreshold  = cert.getQuizThreshold() != null ? cert.getQuizThreshold() * 100.0 : 80.0;
                cs.showText("Pass thresholds - Engagement: " + String.format("%.0f", engThreshold)
                        + "%  |  Quiz: " + String.format("%.0f", qThreshold) + "%");
                cs.endText();

                // ──── 9. Seal Image (replaces old signature placeholder) ────
                try {
                    ClassPathResource sealResource = new ClassPathResource("seal/certifytube_seal.png");
                    byte[] sealBytes;
                    try (InputStream is = sealResource.getInputStream()) {
                        sealBytes = is.readAllBytes();
                    }
                    PDImageXObject sealImage = PDImageXObject.createFromByteArray(doc, sealBytes, "seal");
                    // Place seal in the center-right area
                    float sealSize = 120;
                    float sealX = 380;
                    float sealY = scoresY - 120;
                    cs.drawImage(sealImage, sealX, sealY, sealSize, sealSize);

                    // Label under seal
                    cs.beginText();
                    cs.setFont(fontNormal, 8);
                    cs.setNonStrokingColor(120 / 255f, 120 / 255f, 120 / 255f);
                    cs.newLineAtOffset(sealX + 15, sealY - 12);
                    cs.showText("CertifyTube Official Seal");
                    cs.endText();
                } catch (Exception e) {
                    log.warn("Could not load seal image, skipping: {}", e.getMessage());
                }

                // ──── 10. Verification signature lines (left of seal) ────
                float sigY = scoresY - 80;
                cs.setStrokingColor(180 / 255f, 180 / 255f, 180 / 255f);
                cs.setLineWidth(1);
                cs.moveTo(80, sigY);
                cs.lineTo(200, sigY);
                cs.stroke();

                cs.moveTo(220, sigY);
                cs.lineTo(340, sigY);
                cs.stroke();

                cs.beginText();
                cs.setFont(fontNormal, 9);
                cs.setNonStrokingColor(100 / 255f, 100 / 255f, 100 / 255f);
                cs.newLineAtOffset(80, sigY - 14);
                cs.showText("AI Assessment Engine");
                cs.newLineAtOffset(0, -11);
                cs.showText("Verification Layer 2");
                cs.newLineAtOffset(140, 11);
                cs.showText("Dual-Verification ML");
                cs.newLineAtOffset(0, -11);
                cs.showText("Verification Layer 1");
                cs.endText();

                // ──── 11. Footer ────
                cs.beginText();
                cs.setFont(fontNormal, 9);
                cs.setNonStrokingColor(60 / 255f, 60 / 255f, 60 / 255f);
                cs.newLineAtOffset(80, 55);
                cs.showText("* This certificate verifies engagement and comprehension requirements via dual-layer ML assessment.");
                cs.endText();

                // ──── 12. QR Code ────
                String verifyUrl = publicBaseUrl + "/api/certificates/verify/" + cert.getVerificationToken();
                try {
                    QRCodeWriter qrCodeWriter = new QRCodeWriter();
                    var bitMatrix = qrCodeWriter.encode(verifyUrl, BarcodeFormat.QR_CODE, 120, 120);
                    ByteArrayOutputStream png = new ByteArrayOutputStream();
                    MatrixToImageWriter.writeToStream(bitMatrix, "PNG", png);

                    PDImageXObject qrImage = PDImageXObject.createFromByteArray(doc, png.toByteArray(), "qr");
                    cs.drawImage(qrImage, pageWidth - 200, 55, 120, 120);

                    // "Scan to verify" label
                    cs.beginText();
                    cs.setFont(fontNormal, 8);
                    cs.setNonStrokingColor(120 / 255f, 120 / 255f, 120 / 255f);
                    cs.newLineAtOffset(pageWidth - 195, 46);
                    cs.showText("Scan to verify certificate");
                    cs.endText();
                } catch (Exception e) {
                    log.error("Failed to generate QR code", e);
                }

                // ──── 13. Platform branding (top right) ────
                cs.beginText();
                cs.setFont(fontBold, 24);
                cs.setNonStrokingColor(41 / 255f, 128 / 255f, 185 / 255f);
                cs.newLineAtOffset(pageWidth - 250, pageHeight - 120);
                cs.showText("CertifyTube");
                cs.endText();

                cs.beginText();
                cs.setFont(fontNormal, 10);
                cs.setNonStrokingColor(150 / 255f, 150 / 255f, 150 / 255f);
                cs.newLineAtOffset(pageWidth - 210, pageHeight - 135);
                cs.showText("VERIFIED LEARNING");
                cs.endText();

                // Vertical accent line
                cs.setStrokingColor(41 / 255f, 128 / 255f, 185 / 255f);
                cs.setLineWidth(4);
                cs.moveTo(pageWidth - 270, pageHeight - 100);
                cs.lineTo(pageWidth - 270, pageHeight - 160);
                cs.stroke();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate certificate PDF", e);
        }
    }
}
