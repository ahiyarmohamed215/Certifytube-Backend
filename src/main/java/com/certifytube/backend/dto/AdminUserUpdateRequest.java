package com.certifytube.backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminUserUpdateRequest {
    private String email;
    private String name;
    private String role;
    private Boolean active;
    private Boolean emailVerified;
}
