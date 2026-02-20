package com.certifytube.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignUpRequest {

    @Email
    @NotBlank
    @Size(max = 160)
    private String email;

    @NotBlank
    @Size(min = 8, max = 128)
    private String password;
}
