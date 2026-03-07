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
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.ByteArrayOutputStream;
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

                    String learnerName = user.getEmail().contains("@") 
                            ? user.getEmail().substring(0, user.getEmail().indexOf("@")) 
                            : user.getEmail();

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
                            .createdAtUtc(Instant.now())
                            // PDF generation requires the entity with data, we will generate it, then update
                            .pdfBytes(new byte[0]) 
                            .build());
                    
                    byte[] pdfBytes = generatePdf(cert);
                    cert.setPdfBytes(pdfBytes);
                    certificateRepository.save(cert);

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

    @Transactional(readOnly = true)
    public CertificateResponse verify(String verificationToken) {
        Certificate cert = certificateRepository.findByVerificationToken(verificationToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid certificate link"));
        return toResponse(cert, true);
    }

    private CertificateResponse toResponse(Certificate cert, boolean isPublic) {
        String link = publicBaseUrl + "/api/certificates/verify/" + cert.getVerificationToken();
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
                .engagementScore(cert.getFinalEngagementScore())
                .quizScore(cert.getFinalQuizScore())
                .engagementThreshold(0.85)
                .quizThreshold(0.80)
                .platformName("CertifyTube")
                .platformAttribution("Verification Layer 1 & 2")
                .verificationToken(cert.getVerificationToken())
                .verificationLink(link)
                .createdAtUtc(cert.getCreatedAtUtc().toString())
                .build();
    }

    private byte[] generatePdf(Certificate cert) {
        try (PDDocument doc = new PDDocument()) {
            // Landscape A4
            PDPage page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float pageWidth = page.getMediaBox().getWidth();
                float pageHeight = page.getMediaBox().getHeight();

                // 1. Background / Borders
                cs.setStrokingColor(80 / 255f, 80 / 255f, 80 / 255f); // Dark Grey Outer
                cs.setLineWidth(40);
                cs.addRect(20, 20, pageWidth - 40, pageHeight - 40);
                cs.stroke();

                // Inner light subtle border
                cs.setStrokingColor(230 / 255f, 230 / 255f, 230 / 255f);
                cs.setLineWidth(2);
                cs.addRect(45, 45, pageWidth - 90, pageHeight - 90);
                cs.stroke();

                // 2. Header
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 32);
                cs.setNonStrokingColor(40 / 255f, 40 / 255f, 40 / 255f);
                cs.newLineAtOffset(80, pageHeight - 120);
                cs.showText("Certificate of Completion");
                cs.endText();

                // 3. Proudly presented to
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
                cs.setNonStrokingColor(120 / 255f, 130 / 255f, 140 / 255f);
                cs.newLineAtOffset(80, pageHeight - 170);
                cs.showText("Proudly presented to");
                cs.endText();

                // 4. Learner Name
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 48);
                cs.setNonStrokingColor(30 / 255f, 30 / 255f, 30 / 255f);
                cs.newLineAtOffset(80, pageHeight - 230);
                String name = cert.getLearnerName() != null ? cert.getLearnerName() : "Learner Name";
                cs.showText(name);
                cs.endText();

                // 5. Success text
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
                cs.setNonStrokingColor(60 / 255f, 60 / 255f, 60 / 255f);
                cs.newLineAtOffset(80, pageHeight - 290);
                cs.showText("Has successfully completed the assessment for:");
                cs.newLineAtOffset(0, -20);
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                
                String videoTitle = cert.getVideoTitle();
                if (videoTitle.length() > 60) {
                    videoTitle = videoTitle.substring(0, 57) + "...";
                }
                cs.showText("'" + videoTitle + "'");
                cs.newLineAtOffset(0, -20);
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
                cs.showText("Demonstrating comprehensive understanding and skill proficiency.");
                cs.endText();

                // 6. Meta info pill
                cs.setNonStrokingColor(255 / 255f, 243 / 255f, 219 / 255f); // light yellow tint
                cs.addRect(80, pageHeight - 400, 400, 35);
                cs.fill();

                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                cs.setNonStrokingColor(80 / 255f, 80 / 255f, 80 / 255f);
                cs.newLineAtOffset(90, pageHeight - 388);
                cs.showText("Certificate ID: " + cert.getCertificateId().substring(0, 8));
                
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy").withZone(ZoneId.of("UTC"));
                String dateStr = formatter.format(cert.getCreatedAtUtc());
                cs.newLineAtOffset(200, 0);
                cs.showText("Certified on: " + dateStr);
                cs.endText();

                // 7. Scores Block
                cs.setNonStrokingColor(80 / 255f, 80 / 255f, 80 / 255f);
                cs.addRect(80, pageHeight - 470, 100, 25);
                cs.addRect(80, pageHeight - 505, 100, 25);
                cs.fill();

                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.setNonStrokingColor(255 / 255f, 255 / 255f, 255 / 255f);
                cs.newLineAtOffset(90, pageHeight - 462);
                double quizScore = cert.getFinalQuizScore() != null ? cert.getFinalQuizScore() * 100.0 : 0.0;
                cs.showText("Quiz: " + String.format("%.0f", quizScore) + "%");
                
                cs.newLineAtOffset(0, -35);
                double engScore = cert.getFinalEngagementScore() != null ? cert.getFinalEngagementScore() * 100.0 : 0.0;
                cs.showText("Eng : " + String.format("%.0f", engScore) + "%");
                cs.endText();

                // Signature placeholder box
                cs.setNonStrokingColor(255 / 255f, 243 / 255f, 219 / 255f); // matching yellow
                cs.addRect(210, pageHeight - 505, 300, 60);
                cs.fill();
                
                cs.setStrokingColor(180 / 255f, 180 / 255f, 180 / 255f);
                cs.setLineWidth(1);
                cs.moveTo(230, pageHeight - 472);
                cs.lineTo(330, pageHeight - 472);
                cs.stroke();
                
                cs.moveTo(380, pageHeight - 472);
                cs.lineTo(480, pageHeight - 472);
                cs.stroke();

                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.setNonStrokingColor(100 / 255f, 100 / 255f, 100 / 255f);
                cs.newLineAtOffset(230, pageHeight - 485);
                cs.showText("AI Assessment Engine");
                cs.newLineAtOffset(0, -12);
                cs.showText("Verification Layer 2");
                
                cs.newLineAtOffset(150, 12);
                cs.showText("Dual-Verification ML");
                cs.newLineAtOffset(0, -12);
                cs.showText("Verification Layer 1");
                cs.endText();

                // 8. Footer note
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.setNonStrokingColor(60 / 255f, 60 / 255f, 60 / 255f);
                cs.newLineAtOffset(80, 55);
                cs.showText("* This certificate mathematically verifies engagement and comprehension requirements for the subject.");
                cs.endText();

                // 9. QR Code
                String verifyUrl = publicBaseUrl + "/verify/" + cert.getVerificationToken(); 
                // Alternatively point to /api/certificates/verify or frontend
                try {
                    QRCodeWriter qrCodeWriter = new QRCodeWriter();
                    // Generate matrix
                    var bitMatrix = qrCodeWriter.encode(verifyUrl, BarcodeFormat.QR_CODE, 120, 120);
                    ByteArrayOutputStream png = new ByteArrayOutputStream();
                    MatrixToImageWriter.writeToStream(bitMatrix, "PNG", png);
                    
                    PDImageXObject qrImage = PDImageXObject.createFromByteArray(doc, png.toByteArray(), "qr");
                    cs.drawImage(qrImage, pageWidth - 200, 70, 120, 120);
                } catch (Exception e) {
                    log.error("Failed to generate QR code", e);
                }

                // 10. Platform / Logo placeholder text on top right
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 24);
                cs.setNonStrokingColor(41 / 255f, 128 / 255f, 185 / 255f);
                cs.newLineAtOffset(pageWidth - 250, pageHeight - 120);
                cs.showText("CertifyTube");
                cs.endText();
                
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.setNonStrokingColor(150 / 255f, 150 / 255f, 150 / 255f);
                cs.newLineAtOffset(pageWidth - 210, pageHeight - 135);
                cs.showText("VERIFIED LEARNING");
                cs.endText();

                // Vertical separator edge line
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
