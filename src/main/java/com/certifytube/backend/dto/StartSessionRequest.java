package com.certifytube.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class StartSessionRequest {

    @NotBlank
    @Size(max = 32)
    @JsonAlias({"video_id"})
    private String videoId;

    @NotBlank
    @Size(max = 512)
    @JsonAlias({"video_title"})
    private String videoTitle;
}
