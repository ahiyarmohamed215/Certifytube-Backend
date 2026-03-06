package com.certifytube.backend.controller;

import com.certifytube.backend.dto.AdminEngagementResponse;
import com.certifytube.backend.model.*;
import com.certifytube.backend.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    // ─── Users ────────────────────────────────────────

    @GetMapping("/users")
    public List<UserAccount> getAllUsers() {
        return adminService.getAllUsers();
    }

    @GetMapping("/users/{id}")
    public UserAccount getUserById(@PathVariable Long id) {
        return adminService.getUserById(id);
    }

    @PutMapping("/users/{id}/role")
    public UserAccount updateUserRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
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

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable Long id) {
        adminService.deleteUser(id);
        return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
    }

    // ─── Sessions ─────────────────────────────────────

    @GetMapping("/sessions")
    public List<Session> getAllSessions() {
        return adminService.getAllSessions();
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, String>> deleteSession(@PathVariable String sessionId) {
        adminService.deleteSession(sessionId);
        return ResponseEntity.ok(Map.of("message", "Session deleted successfully"));
    }

    // ─── Certificates ─────────────────────────────────

    @GetMapping("/certificates")
    public List<Certificate> getAllCertificates() {
        return adminService.getAllCertificates();
    }

    @DeleteMapping("/certificates/{certificateId}")
    public ResponseEntity<Map<String, String>> deleteCertificate(@PathVariable String certificateId) {
        adminService.deleteCertificate(certificateId);
        return ResponseEntity.ok(Map.of("message", "Certificate deleted successfully"));
    }

    // ─── Quizzes ──────────────────────────────────────

    @GetMapping("/quizzes")
    public List<Quiz> getAllQuizzes() {
        return adminService.getAllQuizzes();
    }

    @DeleteMapping("/quizzes/{quizId}")
    public ResponseEntity<Map<String, String>> deleteQuiz(@PathVariable String quizId) {
        adminService.deleteQuiz(quizId);
        return ResponseEntity.ok(Map.of("message", "Quiz deleted successfully"));
    }

    // ─── Stats ────────────────────────────────────────

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        return adminService.getStats();
    }

    // ─── Engagement Results (admin only) ──────────────

    @GetMapping("/engagement-results/{sessionId}")
    public AdminEngagementResponse getEngagementResult(@PathVariable String sessionId) {
        return adminService.getEngagementResult(sessionId);
    }
}
