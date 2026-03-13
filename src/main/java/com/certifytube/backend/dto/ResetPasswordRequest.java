package com.certifytube.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequest {

    @NotBlank
    @Size(min = 32, max = 128)
    private String token;

    @NotBlank
    @Size(min = 8, max = 128)
    private String newPassword;
}
