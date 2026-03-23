package com.certifytube.backend.controller;

import com.certifytube.backend.dto.AuthResponse;
import com.certifytube.backend.dto.ChangePasswordRequest;
import com.certifytube.backend.dto.ForgotPasswordRequest;
import com.certifytube.backend.dto.ForgotPasswordResponse;
import com.certifytube.backend.dto.LoginRequest;
import com.certifytube.backend.dto.LogoutResponse;
import com.certifytube.backend.dto.ResendVerificationRequest;
import com.certifytube.backend.dto.ResetPasswordRequest;
import com.certifytube.backend.dto.SignUpRequest;
import com.certifytube.backend.dto.AuthMeResponse;
import com.certifytube.backend.mapper.UserAccountMapper;
import com.certifytube.backend.model.UserAccount;
import com.certifytube.backend.service.AccountDeletionService;
import com.certifytube.backend.service.AuthenticatedUserService;
import com.certifytube.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AccountDeletionService accountDeletionService;
    private final AuthenticatedUserService authenticatedUserService;
    private final UserAccountMapper userAccountMapper;

    @PostMapping("/signup")
    public AuthResponse signUp(@Valid @RequestBody SignUpRequest req) {
        return authService.signUp(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @GetMapping("/me")
    public AuthMeResponse me() {
        UserAccount user = authenticatedUserService.currentUser();
        return userAccountMapper.toAuthMeResponse(user);
    }

    @PostMapping("/logout")
    public LogoutResponse logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.logout(authHeader);
        return new LogoutResponse("Logged out");
    }

    @PostMapping("/signout")
    public LogoutResponse signOut(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.logout(authHeader);
        return new LogoutResponse("Signed out");
    }

    @PostMapping("/forgot-password")
    public ForgotPasswordResponse forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest req,
            HttpServletRequest request) {
        return authService.forgotPassword(req, resolveClientIp(request));
    }

    @PostMapping("/resend-verification")
    public LogoutResponse resendVerification(
            @Valid @RequestBody ResendVerificationRequest req,
            HttpServletRequest request) {
        authService.resendVerification(req, resolveClientIp(request));
        return new LogoutResponse("If the email exists and is not verified, a verification email has been sent");
    }

    @GetMapping("/verify-email")
    public LogoutResponse verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return new LogoutResponse("Email verified successfully");
    }

    @PostMapping("/reset-password")
    public LogoutResponse resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return new LogoutResponse("Password reset successful");
    }

    @PostMapping("/change-password")
    public LogoutResponse changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        UserAccount user = authenticatedUserService.currentUser();
        authService.changePassword(user.getId(), req);
        return new LogoutResponse("Password changed successfully");
    }

    @DeleteMapping("/me")
    public LogoutResponse deleteMyAccount(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        UserAccount user = authenticatedUserService.currentUser();
        authService.logout(authHeader);
        accountDeletionService.deleteUserAndOwnedData(user.getId());
        return new LogoutResponse("Account deleted successfully");
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] parts = xff.split(",");
            if (parts.length > 0 && parts[0] != null && !parts[0].trim().isEmpty()) {
                return parts[0].trim();
            }
        }
        String xrip = request.getHeader("X-Real-IP");
        if (xrip != null && !xrip.isBlank()) {
            return xrip.trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "" : remote;
    }

}
