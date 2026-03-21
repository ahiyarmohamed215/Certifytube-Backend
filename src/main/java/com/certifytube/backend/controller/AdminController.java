package com.certifytube.backend.controller;

import com.certifytube.backend.dto.AdminLearnerProfileResponse;
import com.certifytube.backend.dto.AdminUserSummaryDto;
import com.certifytube.backend.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/learners")
    public List<AdminUserSummaryDto> getLearners() {
        return adminService.getLearners();
    }

    @GetMapping("/learners/{learnerId}/profile")
    public AdminLearnerProfileResponse getLearnerProfile(
            @PathVariable Long learnerId,
            @RequestParam(name = "searchLimit", defaultValue = "30") int searchLimit) {
        return adminService.getLearnerProfile(learnerId, searchLimit);
    }

    @DeleteMapping("/certificates/{certificateId}")
    public ResponseEntity<Map<String, String>> deleteCertificate(@PathVariable String certificateId) {
        adminService.deleteCertificate(certificateId);
        return ResponseEntity.ok(Map.of("message", "Certificate deleted successfully"));
    }
}

