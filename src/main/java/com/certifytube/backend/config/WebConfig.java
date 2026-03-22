package com.certifytube.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.LinkedHashSet;
import java.util.Set;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    @Value("${app.cors.allowed-origin-patterns:}")
    private String extraAllowedOriginPatterns;

    private String[] resolvedOriginPatterns() {
        Set<String> patterns = new LinkedHashSet<>();
        patterns.add(frontendBaseUrl);
        patterns.add("http://localhost:*");
        patterns.add("http://127.0.0.1:*");
        patterns.add("https://*.vercel.app");

        if (extraAllowedOriginPatterns != null && !extraAllowedOriginPatterns.isBlank()) {
            for (String pattern : extraAllowedOriginPatterns.split(",")) {
                String trimmed = pattern.trim();
                if (!trimmed.isEmpty()) {
                    patterns.add(trimmed);
                }
            }
        }

        return patterns.toArray(new String[0]);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(resolvedOriginPatterns())
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
