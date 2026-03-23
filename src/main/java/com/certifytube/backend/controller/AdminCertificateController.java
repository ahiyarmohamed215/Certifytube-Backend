package com.certifytube.backend.controller;

import com.certifytube.backend.service.CertificateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
        certificateService.revoke(certificateId);
        return ResponseEntity.ok(Map.of(
                "message", "Certificate revoked successfully",
                "certificateId", certificateId,
                "status", "REVOKED"
        ));
    }

    @PostMapping("/{certificateId}/activate")
    public ResponseEntity<Map<String, String>> activate(@PathVariable String certificateId) {
        certificateService.activate(certificateId);
        return ResponseEntity.ok(Map.of(
                "message", "Certificate activated successfully",
                "certificateId", certificateId,
                "status", "ACTIVE"
        ));
    }
}
