package com.certifytube.backend.controller;

import com.certifytube.backend.dto.AuthMeResponse;
import com.certifytube.backend.mapper.UserAccountMapper;
import com.certifytube.backend.model.Role;
import com.certifytube.backend.model.UserAccount;
import com.certifytube.backend.service.AuthService;
import com.certifytube.backend.service.AuthenticatedUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;
    @Mock
    private AuthenticatedUserService authenticatedUserService;
    @Mock
    private UserAccountMapper userAccountMapper;

    @InjectMocks
    private AuthController authController;

    @Test
    void meShouldReturnNameFromCurrentUserRecord() {
        UserAccount currentUser = UserAccount.builder()
                .id(1L)
                .email("user@example.com")
                .role(Role.LEARNER)
                .name("John Doe")
                .build();

        AuthMeResponse mappedResponse = AuthMeResponse.builder()
                .userId(1L)
                .email("user@example.com")
                .role("LEARNER")
                .name("John Doe")
                .build();

        when(authenticatedUserService.currentUser()).thenReturn(currentUser);
        when(userAccountMapper.toAuthMeResponse(currentUser)).thenReturn(mappedResponse);

        AuthMeResponse response = authController.me();

        assertEquals("John Doe", response.getName());
        assertEquals("user@example.com", response.getEmail());
        assertEquals("LEARNER", response.getRole());
    }
}
