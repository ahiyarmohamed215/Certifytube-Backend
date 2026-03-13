package com.certifytube.backend.controller;

import com.certifytube.backend.dto.AdminEngagementResponse;
import com.certifytube.backend.dto.AdminLearnerProfileResponse;
import com.certifytube.backend.dto.AdminUserActiveRequest;
import com.certifytube.backend.dto.AdminUserSummaryDto;
import com.certifytube.backend.dto.AdminUserUpdateRequest;
import com.certifytube.backend.model.Certificate;
import com.certifytube.backend.model.Quiz;
import com.certifytube.backend.model.Role;
import com.certifytube.backend.model.Session;
import com.certifytube.backend.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    // Users

    @GetMapping("/users")
    public List<AdminUserSummaryDto> getAllUsers() {
        return adminService.getAllUsers();
    }

    @GetMapping("/users/{id}")
    public AdminUserSummaryDto getUserById(@PathVariable Long id) {
        return adminService.getUserById(id);
    }

    @PutMapping("/users/{id}/role")
    public AdminUserSummaryDto updateUserRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String roleStr = body.get("role");
        if (roleStr == null || roleStr.isBlank()) {
            throw new IllegalArgumentException("Role is required");
        }
        Role role;
        try {
            role = Role.valueOf(roleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role: " + roleStr + ". Valid roles: ADMIN, LEARNER");
        }
        return adminService.updateUserRole(id, role);
    }

    @PutMapping("/users/{id}")
    public AdminUserSummaryDto updateUser(@PathVariable Long id, @RequestBody AdminUserUpdateRequest req) {
        return adminService.updateUser(id, req);
    }

    @PatchMapping("/users/{id}/active")
    public AdminUserSummaryDto setUserActive(@PathVariable Long id, @RequestBody AdminUserActiveRequest req) {
        if (req.getActive() == null) {
            throw new IllegalArgumentException("active is required");
        }
        return adminService.setUserActive(id, req.getActive());
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable Long id) {
        adminService.deleteUser(id);
        return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
    }

    // Learners profile view

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

    @GetMapping("/youtube-searches")
    public List<AdminLearnerProfileResponse.YouTubeSearchInsight> getYouTubeSearches(
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return adminService.getYouTubeSearches(limit);
    }

    // Sessions

    @GetMapping("/sessions")
    public List<Session> getAllSessions() {
        return adminService.getAllSessions();
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, String>> deleteSession(@PathVariable String sessionId) {
        adminService.deleteSession(sessionId);
        return ResponseEntity.ok(Map.of("message", "Session deleted successfully"));
    }

    // Certificates

    @GetMapping("/certificates")
    public List<Certificate> getAllCertificates() {
        return adminService.getAllCertificates();
    }

    @DeleteMapping("/certificates/{certificateId}")
    public ResponseEntity<Map<String, String>> deleteCertificate(@PathVariable String certificateId) {
        adminService.deleteCertificate(certificateId);
        return ResponseEntity.ok(Map.of("message", "Certificate deleted successfully"));
    }

    // Quizzes

    @GetMapping("/quizzes")
    public List<Quiz> getAllQuizzes() {
        return adminService.getAllQuizzes();
    }

    @DeleteMapping("/quizzes/{quizId}")
    public ResponseEntity<Map<String, String>> deleteQuiz(@PathVariable String quizId) {
        adminService.deleteQuiz(quizId);
        return ResponseEntity.ok(Map.of("message", "Quiz deleted successfully"));
    }

    // Stats

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        return adminService.getStats();
    }

    // Engagement results

    @GetMapping("/engagement-results/{sessionId}")
    public AdminEngagementResponse getEngagementResult(@PathVariable String sessionId) {
        return adminService.getEngagementResult(sessionId);
    }
}

