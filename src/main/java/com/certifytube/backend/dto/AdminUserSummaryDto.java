package com.certifytube.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

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
    private Instant emailVerifiedAtUtc;
    private Instant createdAtUtc;
    private Long sessionCount;
    private Long certificateCount;
}
