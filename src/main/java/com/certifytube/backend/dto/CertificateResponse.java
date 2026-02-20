package com.certifytube.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CertificateResponse {
    private String certificateId;
    private String certificateNumber;
    private String sessionId;
    private Long userId;
    private double scorePercent;
    private String verificationToken;
    private String verificationLink;
    private String createdAtUtc;
}
