package com.certifytube.backend.controller;

import com.certifytube.backend.dto.CertificateResponse;
import com.certifytube.backend.model.UserAccount;
import com.certifytube.backend.service.AuthenticatedUserService;
import com.certifytube.backend.service.CertificateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/certificates")
public class CertificateController {

    private final CertificateService certificateService;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping("/{certificateId}")
    public CertificateResponse getMine(@PathVariable String certificateId) {
        UserAccount user = authenticatedUserService.currentUser();
        log.info("User {} requesting certificate {}", user.getId(), certificateId);
        return certificateService.getOwnedCertificate(user.getId(), certificateId);
    }

    @GetMapping("/{certificateId}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String certificateId) {
        UserAccount user = authenticatedUserService.currentUser();
        log.info("User {} downloading PDF for certificate {}", user.getId(), certificateId);
        byte[] pdf = certificateService.getOwnedCertificatePdf(user.getId(), certificateId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"certificate-" + certificateId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/verify/{token}")
    public CertificateResponse verify(@PathVariable String token) {
        log.info("Verifying certificate with token {}", token);
        CertificateResponse response = certificateService.verify(token);
        log.info("Certificate {} successfully verified via token", response.getCertificateId());
        return response;
    }
}
