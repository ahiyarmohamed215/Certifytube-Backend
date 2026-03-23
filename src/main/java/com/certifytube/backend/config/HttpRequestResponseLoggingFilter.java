package com.certifytube.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
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
        MDC.put("rid", requestId);

        try {
            long startedAt = System.nanoTime();
            String method = wrappedRequest.getMethod();
            String path = wrappedRequest.getRequestURI();
            String query = wrappedRequest.getQueryString();
            String loggedPath = sanitizePath(path);
            String loggedQuery = sanitizeQuery(query);
            String origin = safeHeader(wrappedRequest, "Origin");
            String requestIp = clientIp(wrappedRequest);
            String userAgent = truncate(safeHeader(wrappedRequest, "User-Agent"), 200);
            String contentType = wrappedRequest.getContentType();
            long contentLength = wrappedRequest.getContentLengthLong();

            if (log.isDebugEnabled()) {
                log.debug(
                        "HTTP_REQ_START rid={} method={} path={} query={} origin={} ip={} ua={} contentType={} contentLength={}",
                        requestId,
                        method,
                        loggedPath,
                        loggedQuery,
                        origin,
                        requestIp,
                        userAgent,
                        contentType,
                        contentLength
                );
            }

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
                String routePattern = resolveRoutePattern(wrappedRequest, loggedPath);

                if (failure != null || status >= 500) {
                    String errorType = failure == null ? "" : failure.getClass().getSimpleName();
                    String errorMessage = failure == null || failure.getMessage() == null
                            ? ""
                            : truncate(failure.getMessage(), 300);
                    log.error(
                            "HTTP_ACCESS rid={} method={} path={} route={} query={} status={} durationMs={} ip={} origin={} reqBytes={} resBytes={} ua={} errorType={} errorMessage={}",
                            requestId, method, loggedPath, routePattern, loggedQuery, status, durationMs, requestIp,
                            origin, reqBytes, resBytes, userAgent, errorType, errorMessage
                    );
                } else if (status >= 400) {
                    log.warn(
                            "HTTP_ACCESS rid={} method={} path={} route={} query={} status={} durationMs={} ip={} origin={} reqBytes={} resBytes={} ua={}",
                            requestId, method, loggedPath, routePattern, loggedQuery, status, durationMs, requestIp,
                            origin, reqBytes, resBytes, userAgent
                    );
                } else {
                    log.info(
                            "HTTP_ACCESS rid={} method={} path={} route={} query={} status={} durationMs={} ip={} origin={} reqBytes={} resBytes={} ua={}",
                            requestId, method, loggedPath, routePattern, loggedQuery, status, durationMs, requestIp,
                            origin, reqBytes, resBytes, userAgent
                    );
                }

                if (includeBody) {
                    logBodies(requestId, wrappedRequest, wrappedResponse);
                }

                wrappedResponse.copyBodyToResponse();
            }
        } finally {
            MDC.remove("rid");
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
            log.debug("HTTP_REQ_BODY rid={} body={}", requestId, truncate(reqBody, maxBodyChars));
        }

        if (isTextContent(resContentType)) {
            String resBody = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
            resBody = sanitizeSensitive(resBody);
            log.debug("HTTP_RES_BODY rid={} body={}", requestId, truncate(resBody, maxBodyChars));
        }
    }

    private String resolveRoutePattern(HttpServletRequest request, String fallbackPath) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern == null) {
            return fallbackPath;
        }
        String route = pattern.toString();
        return route.isBlank() ? fallbackPath : route;
    }

    private String sanitizePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        return path.replaceAll("(?i)(/verify/)[^/?]+", "$1***");
    }

    private String sanitizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        return query
                .replaceAll("(?i)(token=)[^&]*", "$1***")
                .replaceAll("(?i)(password=)[^&]*", "$1***")
                .replaceAll("(?i)(newPassword=)[^&]*", "$1***")
                .replaceAll("(?i)(currentPassword=)[^&]*", "$1***");
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
