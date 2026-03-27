package com.certifytube.backend.client;

import com.certifytube.backend.exception.MlServiceUnavailableException;
import com.certifytube.backend.exception.NotFoundException;
import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MlServiceClient {

        @Value("${ml.base-url}")
        private String mlBaseUrl;

        @Value("${ml.client.connect-timeout-ms:120000}")
        private int connectTimeoutMs;

        @Value("${ml.client.response-timeout-ms:120000}")
        private long responseTimeoutMs;

        private WebClient client() {
                HttpClient httpClient = HttpClient.create()
                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                                .responseTimeout(Duration.ofMillis(responseTimeoutMs));
                return WebClient.builder()
                                .baseUrl(mlBaseUrl)
                                .clientConnector(new ReactorClientHttpConnector(httpClient))
                                .build();
        }

        private String bodyForError(WebClientResponseException e) {
                String body = e.getResponseBodyAsString();
                if (body == null || body.isBlank()) {
                        return "(empty response)";
                }
                String trimmed = body.trim();
                return trimmed.length() > 500 ? trimmed.substring(0, 500) + "..." : trimmed;
        }

        /**
         * Call the ML engagement analysis endpoint with raw events.
         */
        @SuppressWarnings("unchecked")
        public Map<String, Object> analyzeEngagement(
                        String sessionId,
                        String featureVersion,
                        List<Map<String, Object>> events,
                        String model) {

                Map<String, Object> payload = Map.of(
                                "session_id", sessionId,
                                "feature_version", featureVersion,
                                "events", events);

                String endpoint = "/engagement/analyze/" + model;
                log.debug("ml.engagement.request endpoint={} sessionId={} eventCount={}", endpoint, sessionId,
                                events != null ? events.size() : 0);

                try {
                        return client()
                                        .post()
                                        .uri(endpoint)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .bodyValue(payload)
                                        .retrieve()
                                        .bodyToMono(Map.class)
                                        .block();
                } catch (WebClientResponseException e) {
                        int status = e.getStatusCode().value();
                        log.error("ml.engagement.failed status={} endpoint={}{} reason={}",
                                        e.getStatusCode().value(), mlBaseUrl, endpoint, e.getResponseBodyAsString());
                        if (status == 400) {
                                throw new IllegalArgumentException(
                                                "ML engagement request was invalid (HTTP 400): " + bodyForError(e), e);
                        }
                        if (status == 503) {
                                throw new MlServiceUnavailableException(
                                                "ML engagement service is unavailable (HTTP 503).", e);
                        }
                        throw new IllegalStateException(
                                        "ML service rejected engagement request (HTTP %d). Verify model endpoint and payload."
                                                        .formatted(status),
                                        e);
                } catch (WebClientRequestException e) {
                        log.error("ml.engagement.unreachable endpoint={}{} reason={}", mlBaseUrl, endpoint, e.getMessage());
                        throw new MlServiceUnavailableException(
                                        "Cannot reach ML service at %s. Set ML_BASE_URL to a reachable ML API."
                                                        .formatted(mlBaseUrl), e);
                } catch (RuntimeException e) {
                        log.error("ml.engagement.unexpected endpoint={}{} reason={}", mlBaseUrl, endpoint, e.getMessage());
                        throw new IllegalStateException(
                                        "ML engagement request timed out or failed unexpectedly. Check ML service health and ML_BASE_URL.",
                                        e);
                }
        }

        /**
         * Call the ML quiz generation endpoint.
         */
        @SuppressWarnings("unchecked")
        public Map<String, Object> generateQuiz(
                        String sessionId,
                        String videoId,
                        double videoDurationSec) {

                Map<String, Object> payload = new HashMap<>();
                payload.put("session_id", sessionId);
                payload.put("video_id", videoId);
                payload.put("video_duration_sec", videoDurationSec);

                log.debug("ml.quiz.generate.request endpoint=/quiz/generate sessionId={} videoId={} durationSec={}",
                                sessionId,
                                videoId,
                                videoDurationSec);

                try {
                        return client()
                                        .post()
                                        .uri("/quiz/generate")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .bodyValue(payload)
                                        .retrieve()
                                        .bodyToMono(Map.class)
                                        .block();
                } catch (WebClientResponseException e) {
                        int status = e.getStatusCode().value();
                        log.error("ml.quiz.generate.failed status={} reason={}", status, e.getResponseBodyAsString());
                        if (status == 400) {
                                throw new IllegalArgumentException(
                                                "ML quiz generation request was invalid (HTTP 400): " + bodyForError(e), e);
                        }
                        if (status == 503) {
                                throw new MlServiceUnavailableException(
                                                "ML quiz generation service is unavailable (HTTP 503).", e);
                        }
                        throw new IllegalStateException(
                                        "ML quiz generation failed (HTTP %d): %s".formatted(status, bodyForError(e)), e);
                } catch (WebClientRequestException e) {
                        log.error("ml.quiz.generate.unreachable endpoint={}{} reason={}", mlBaseUrl, "/quiz/generate", e.getMessage());
                        throw new MlServiceUnavailableException(
                                        "Cannot reach ML service at %s. Set ML_BASE_URL to a reachable ML API."
                                                        .formatted(mlBaseUrl), e);
                }
        }

        /**
         * Call the ML quiz grading endpoint.
         */
        @SuppressWarnings("unchecked")
        public Map<String, Object> gradeQuiz(
                        String quizId,
                        String sessionId,
                        String videoId,
                        List<Map<String, Object>> answers) {

                Map<String, Object> payload = new HashMap<>();
                payload.put("quiz_id", quizId);
                payload.put("session_id", sessionId);
                payload.put("video_id", videoId);
                payload.put("answers", answers);

                log.debug("ml.quiz.grade.request endpoint=/quiz/grade quizId={} sessionId={} answerCount={}",
                                quizId,
                                sessionId,
                                answers != null ? answers.size() : 0);

                try {
                        return client()
                                        .post()
                                        .uri("/quiz/grade")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .bodyValue(payload)
                                        .retrieve()
                                        .bodyToMono(Map.class)
                                        .block();
                } catch (WebClientResponseException e) {
                        int status = e.getStatusCode().value();
                        log.error("ml.quiz.grade.failed status={} quizId={} reason={}", status, quizId,
                                        e.getResponseBodyAsString());
                        if (status == 404) {
                                throw new NotFoundException("ML quiz not found for quiz_id: " + quizId);
                        }
                        if (status == 400) {
                                throw new IllegalArgumentException(
                                                "ML quiz grading request was invalid (HTTP 400): " + bodyForError(e), e);
                        }
                        if (status == 503) {
                                throw new MlServiceUnavailableException(
                                                "ML quiz grading service is unavailable (HTTP 503).", e);
                        }
                        throw new IllegalStateException(
                                        "ML quiz grading failed (HTTP %d): %s".formatted(status, bodyForError(e)), e);
                } catch (WebClientRequestException e) {
                        log.error("ml.quiz.grade.unreachable endpoint={}{} reason={}", mlBaseUrl, "/quiz/grade", e.getMessage());
                        throw new MlServiceUnavailableException(
                                        "Cannot reach ML service at %s. Set ML_BASE_URL to a reachable ML API."
                                                        .formatted(mlBaseUrl), e);
                }
        }
}
