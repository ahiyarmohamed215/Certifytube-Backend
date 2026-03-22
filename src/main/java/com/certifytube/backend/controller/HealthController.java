package com.certifytube.backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.util.Map;

@RestController
public class HealthController {

    @Value("${spring.application.name:backend}")
    private String appName;

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "service", appName,
                "timezone", ZoneId.systemDefault().getId()
        );
    }
}
