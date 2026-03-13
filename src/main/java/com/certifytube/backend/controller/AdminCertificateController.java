package com.certifytube.backend.controller;

import com.certifytube.backend.service.CertificateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/certificates")
public class AdminCertificateController {

    private final CertificateService certificateService;

    /**
     * Revoke a certificate. Only accessible by ADMIN role.
     * Security is enforced by SecurityConfig: .requestMatchers("/api/admin/**").hasRole("ADMIN")
     */
    @PostMapping("/{certificateId}/revoke")
    public ResponseEntity<Map<String, String>> revoke(@PathVariable String certificateId) {
        log.info("Admin revoking certificate {}", certificateId);
        certificateService.revoke(certificateId);
        return ResponseEntity.ok(Map.of(
                "message", "Certificate revoked successfully",
                "certificateId", certificateId,
                "status", "REVOKED"
        ));
    }

    @PostMapping("/{certificateId}/activate")
    public ResponseEntity<Map<String, String>> activate(@PathVariable String certificateId) {
        log.info("Admin activating certificate {}", certificateId);
        certificateService.activate(certificateId);
        return ResponseEntity.ok(Map.of(
                "message", "Certificate activated successfully",
                "certificateId", certificateId,
                "status", "ACTIVE"
        ));
    }
}
