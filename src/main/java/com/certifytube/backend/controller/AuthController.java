package com.certifytube.backend.controller;

import com.certifytube.backend.dto.AuthResponse;
import com.certifytube.backend.dto.LoginRequest;
import com.certifytube.backend.dto.LogoutResponse;
import com.certifytube.backend.dto.SignUpRequest;
import com.certifytube.backend.dto.AuthMeResponse;
import com.certifytube.backend.mapper.UserAccountMapper;
import com.certifytube.backend.model.UserAccount;
import com.certifytube.backend.service.AuthenticatedUserService;
import com.certifytube.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
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
}
