package com.certifytube.backend.service;

import com.certifytube.backend.dto.AuthResponse;
import com.certifytube.backend.dto.LoginRequest;
import com.certifytube.backend.dto.SignUpRequest;
import com.certifytube.backend.mapper.UserAccountMapper;
import com.certifytube.backend.model.Role;
import com.certifytube.backend.model.UserAccount;
import com.certifytube.backend.repository.RevokedTokenRepository;
import com.certifytube.backend.repository.UserAccountRepository;
import com.certifytube.backend.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private RevokedTokenRepository revokedTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private UserAccountMapper userAccountMapper;

    @InjectMocks
    private AuthService authService;

    @Test
    void signUpShouldPersistTrimmedNameAndReturnIt() {
        SignUpRequest req = new SignUpRequest();
        req.setEmail("  User@Example.com ");
        req.setPassword("securePassword123");
        req.setName("  John Doe  ");

        when(userAccountRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("securePassword123")).thenReturn("hashed-password");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount user = invocation.getArgument(0);
            user.setId(11L);
            return user;
        });
        when(jwtService.generateToken("user@example.com", "LEARNER")).thenReturn("jwt-token");
        when(userAccountMapper.toAuthResponse(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount user = invocation.getArgument(0);
            return AuthResponse.builder()
                    .userId(user.getId())
                    .email(user.getEmail())
                    .role(user.getRole().name())
                    .name(user.getName())
                    .build();
        });

        AuthResponse response = authService.signUp(req);

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(userCaptor.capture());
        UserAccount savedUser = userCaptor.getValue();

        assertEquals("user@example.com", savedUser.getEmail());
        assertEquals("John Doe", savedUser.getName());
        assertEquals("John Doe", response.getName());
        assertEquals("jwt-token", response.getToken());
        assertEquals("Bearer", response.getTokenType());
        assertTrue(response.getMessage().contains("John Doe"));
    }

    @Test
    void loginShouldReturnNameFromDbUserRecord() {
        LoginRequest req = new LoginRequest();
        req.setEmail("  user@example.com ");
        req.setPassword("securePassword123");

        UserAccount user = UserAccount.builder()
                .id(10L)
                .email("user@example.com")
                .name("John Doe")
                .passwordHash("hashed-password")
                .role(Role.LEARNER)
                .createdAtUtc(Instant.now())
                .build();

        when(userAccountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("securePassword123", "hashed-password")).thenReturn(true);
        when(jwtService.generateToken("user@example.com", "LEARNER")).thenReturn("jwt-login");
        when(userAccountMapper.toAuthResponse(user)).thenReturn(AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .role(user.getRole().name())
                .name(user.getName())
                .build());

        AuthResponse response = authService.login(req);

        assertEquals("John Doe", response.getName());
        assertEquals("jwt-login", response.getToken());
        assertEquals("Bearer", response.getTokenType());
        assertNull(response.getMessage());
    }
}
