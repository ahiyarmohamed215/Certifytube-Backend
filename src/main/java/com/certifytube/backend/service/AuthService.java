package com.certifytube.backend.service;

import com.certifytube.backend.dto.AuthResponse;
import com.certifytube.backend.dto.LoginRequest;
import com.certifytube.backend.dto.SignUpRequest;
import com.certifytube.backend.mapper.UserAccountMapper;
import com.certifytube.backend.model.Role;
import com.certifytube.backend.model.RevokedToken;
import com.certifytube.backend.model.UserAccount;
import com.certifytube.backend.repository.RevokedTokenRepository;
import com.certifytube.backend.repository.UserAccountRepository;
import com.certifytube.backend.security.JwtService;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final RevokedTokenRepository revokedTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserAccountMapper userAccountMapper;

    @Transactional
    public AuthResponse signUp(SignUpRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        String name = req.getName().trim();
        if (name.length() < 2 || name.length() > 255) {
            throw new IllegalArgumentException("Name must be between 2 and 255 characters");
        }
        if (userAccountRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }

        UserAccount user = userAccountRepository.save(UserAccount.builder()
                .email(email)
                .name(name)
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(Role.LEARNER)
                .createdAtUtc(Instant.now())
                .build());

        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        AuthResponse response = userAccountMapper.toAuthResponse(user);
        response.setToken(token);
        response.setTokenType("Bearer");
        response.setMessage("Signup successful! Your name (" + user.getName() + ") will be used in your certificate.");
        return response;
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        UserAccount user = userAccountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        AuthResponse response = userAccountMapper.toAuthResponse(user);
        response.setToken(token);
        response.setTokenType("Bearer");
        return response;
    }

    @Transactional
    public void logout(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank())
            return;
        String token = bearerToken.startsWith("Bearer ") ? bearerToken.substring(7) : bearerToken;

        try {
            String jti = jwtService.extractJti(token);
            Instant exp = jwtService.extractExpiration(token);
            if (jti != null && exp != null && exp.isAfter(Instant.now()) && !revokedTokenRepository.existsByJti(jti)) {
                revokedTokenRepository.save(RevokedToken.builder()
                        .jti(jti)
                        .expiresAtUtc(exp)
                        .revokedAtUtc(Instant.now())
                        .build());
            }
            revokedTokenRepository.deleteByExpiresAtUtcBefore(Instant.now());
        } catch (JwtException | IllegalArgumentException ignored) {
            // Invalid/expired token can be treated as already logged out.
        }
    }
}
