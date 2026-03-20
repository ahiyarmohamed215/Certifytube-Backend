package com.certifytube.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUserSummaryDto {
    private Long userId;
    private String email;
    private String name;
    private String role;
    private Boolean active;
    private Boolean emailVerified;
    private LocalDateTime emailVerifiedAtUtc;
    private LocalDateTime createdAtUtc;
    private Long sessionCount;
    private Long certificateCount;
}
