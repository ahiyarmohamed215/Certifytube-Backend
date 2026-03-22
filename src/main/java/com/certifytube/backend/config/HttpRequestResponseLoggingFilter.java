package com.certifytube.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class HttpRequestResponseLoggingFilter extends OncePerRequestFilter {

    @Value("${app.http.logging.enabled:true}")
    private boolean loggingEnabled;

    @Value("${app.http.logging.include-body:false}")
    private boolean includeBody;

    @Value("${app.http.logging.max-body-chars:2000}")
    private int maxBodyChars;

    @Value("${app.http.logging.skip-paths:}")
    private String skipPathsRaw;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!loggingEnabled) {
            return true;
        }
        Set<String> skipPaths = Arrays.stream(skipPathsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        return skipPaths.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        int requestCacheLimit = Math.max(maxBodyChars * 4, 8192);
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request, requestCacheLimit);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }
        wrappedResponse.setHeader("X-Request-Id", requestId);

        long startedAt = System.nanoTime();
        String method = wrappedRequest.getMethod();
        String path = wrappedRequest.getRequestURI();
        String query = wrappedRequest.getQueryString();

        log.info(
                "HTTP_REQ_START rid={} method={} path={} query={} origin={} ip={} ua={} contentType={} contentLength={}",
                requestId,
                method,
                path,
                query == null ? "" : query,
                safeHeader(wrappedRequest, "Origin"),
                clientIp(wrappedRequest),
                truncate(safeHeader(wrappedRequest, "User-Agent"), 200),
                wrappedRequest.getContentType(),
                wrappedRequest.getContentLengthLong()
        );

        Throwable failure = null;
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } catch (Throwable t) {
            failure = t;
            throw t;
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            int status = wrappedResponse.getStatus();
            int reqBytes = wrappedRequest.getContentAsByteArray().length;
            int resBytes = wrappedResponse.getContentAsByteArray().length;

            if (failure == null) {
                log.info(
                        "HTTP_REQ_END rid={} method={} path={} status={} durationMs={} reqBytes={} resBytes={}",
                        requestId, method, path, status, durationMs, reqBytes, resBytes
                );
            } else {
                log.error(
                        "HTTP_REQ_ERROR rid={} method={} path={} status={} durationMs={} errorType={} errorMessage={}",
                        requestId,
                        method,
                        path,
                        status,
                        durationMs,
                        failure.getClass().getSimpleName(),
                        failure.getMessage()
                );
            }

            if (includeBody) {
                logBodies(requestId, wrappedRequest, wrappedResponse);
            }

            wrappedResponse.copyBodyToResponse();
        }
    }

    private void logBodies(
            String requestId,
            ContentCachingRequestWrapper request,
            ContentCachingResponseWrapper response
    ) {
        String reqContentType = request.getContentType();
        String resContentType = response.getContentType();

        if (isTextContent(reqContentType)) {
            String reqBody = new String(request.getContentAsByteArray(), StandardCharsets.UTF_8);
            reqBody = sanitizeSensitive(reqBody);
            log.info("HTTP_REQ_BODY rid={} body={}", requestId, truncate(reqBody, maxBodyChars));
        }

        if (isTextContent(resContentType)) {
            String resBody = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
            resBody = sanitizeSensitive(resBody);
            log.info("HTTP_RES_BODY rid={} body={}", requestId, truncate(resBody, maxBodyChars));
        }
    }

    private boolean isTextContent(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String c = contentType.toLowerCase();
        return c.startsWith("application/json")
                || c.startsWith("application/xml")
                || c.startsWith("application/x-www-form-urlencoded")
                || c.startsWith("text/");
    }

    private String sanitizeSensitive(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return text
                .replaceAll("(?i)(\"password\"\\s*:\\s*\")[^\"]*(\")", "$1***$2")
                .replaceAll("(?i)(\"newPassword\"\\s*:\\s*\")[^\"]*(\")", "$1***$2")
                .replaceAll("(?i)(\"currentPassword\"\\s*:\\s*\")[^\"]*(\")", "$1***$2")
                .replaceAll("(?i)(\"token\"\\s*:\\s*\")[^\"]*(\")", "$1***$2");
    }

    private String truncate(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "...";
    }

    private String safeHeader(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        return value == null ? "" : value;
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] parts = xff.split(",");
            if (parts.length > 0 && parts[0] != null && !parts[0].trim().isEmpty()) {
                return parts[0].trim();
            }
        }
        String xrip = request.getHeader("X-Real-IP");
        if (xrip != null && !xrip.isBlank()) {
            return xrip.trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "" : remote;
    }
}
