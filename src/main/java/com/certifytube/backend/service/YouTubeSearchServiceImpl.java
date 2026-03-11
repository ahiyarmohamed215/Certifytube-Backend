package com.certifytube.backend.service;

import com.certifytube.backend.client.YouTubeClient;
import com.certifytube.backend.dto.YouTubeSearchResponse;
import com.certifytube.backend.dto.YouTubeVideoDto;
import com.certifytube.backend.model.YouTubeSearchCache;
import com.certifytube.backend.model.YouTubeSearchCacheItem;
import com.certifytube.backend.model.YouTubeVideoCache;
import com.certifytube.backend.repository.YouTubeSearchCacheItemRepository;
import com.certifytube.backend.repository.YouTubeSearchCacheRepository;
import com.certifytube.backend.repository.YouTubeVideoCacheRepository;
import com.certifytube.backend.util.StemCategoryUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class YouTubeSearchServiceImpl implements YouTubeSearchService {

    private final YouTubeClient youTubeClient;
    private final YouTubeSearchCacheRepository searchCacheRepository;
    private final YouTubeSearchCacheItemRepository cacheItemRepository;
    private final YouTubeVideoCacheRepository videoCacheRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_VIDEOS_PER_QUERY = 30;
    private static final int DEFAULT_LIMIT = 20;
    private static final Pattern YT_SHORT_PATTERN = Pattern.compile("youtu\\.be/([A-Za-z0-9_-]{11})");
    private static final Pattern YT_WATCH_PATTERN = Pattern.compile("[?&]v=([A-Za-z0-9_-]{11})");
    private static final Pattern YT_SHORTS_PATTERN = Pattern.compile("/shorts/([A-Za-z0-9_-]{11})");

    // STEM_CATEGORIES constant removed – StemCategoryUtil handles both category and keyword checks.

    private static final Logger log = LoggerFactory.getLogger(YouTubeSearchServiceImpl.class);

    @Override
    @Transactional
    public YouTubeSearchResponse searchVideos(String query, int requestedLimit) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query is required");
        }

        String input = query.trim();
        int limit = normalizeLimit(requestedLimit);

        Optional<String> videoId = extractVideoId(input);
        if (videoId.isPresent()) {
            return buildSingleVideoResponse(input, videoId.get());
        }

        String normalizedQuery = normalizeQuery(input);
        YouTubeSearchCache cache = refreshQueryCacheIfNeeded(normalizedQuery);
        List<YouTubeVideoDto> videos = loadFromCache(cache, limit);

        YouTubeSearchResponse response = YouTubeSearchResponse.builder()
                .query(normalizedQuery)
                .count(videos.size())
                .videos(videos)
                .build();
        return response;
    }

    @Override
    @Transactional
    public YouTubeSearchResponse searchStemVideos(String query, int requestedLimit) {
        // For STEM filtering: try to get more results then filter
        YouTubeSearchResponse response = searchVideos(query, Math.min(requestedLimit * 2, MAX_VIDEOS_PER_QUERY));
        List<YouTubeVideoDto> stemVideos = response.getVideos().stream()
                .filter(v -> StemCategoryUtil.isStemContent(v.getCategoryId(), v.getTitle(), v.getDescription()))
                .limit(requestedLimit)
                .toList();
        return YouTubeSearchResponse.builder()
                .query(response.getQuery())
                .count(stemVideos.size())
                .videos(stemVideos)
                .build();
    }

    @Transactional
    protected YouTubeSearchCache refreshQueryCacheIfNeeded(String normalizedQuery) {
        LocalDate today = LocalDate.now();

        YouTubeSearchCache cache = searchCacheRepository.findByQueryText(normalizedQuery)
                .orElseGet(() -> {
                    Instant now = Instant.now();
                    return searchCacheRepository.save(YouTubeSearchCache.builder()
                            .queryText(normalizedQuery)
                            .lastRefreshedOn(LocalDate.of(1970, 1, 1))
                            .createdAtUtc(now)
                            .updatedAtUtc(now)
                            .build());
                });

        List<YouTubeSearchCacheItem> existingItems = cacheItemRepository.findByCacheOrderByPositionIndexAsc(cache);
        boolean hasFreshData = cache.getLastRefreshedOn() != null
                && cache.getLastRefreshedOn().isEqual(today)
                && !existingItems.isEmpty();
        if (hasFreshData) {
            return cache;
        }

        log.info("Fetching from YouTube API for query: {}", normalizedQuery);
        String rawJson = youTubeClient.searchVideos(normalizedQuery, MAX_VIDEOS_PER_QUERY);
        List<YouTubeVideoDto> fetched = mapSearchVideos(rawJson);

        cacheItemRepository.deleteByCache(cache);
        cacheItemRepository.flush();
        int position = 1;
        for (YouTubeVideoDto dto : fetched) {
            YouTubeVideoCache video = upsertVideo(dto);
            cacheItemRepository.save(YouTubeSearchCacheItem.builder()
                    .cache(cache)
                    .video(video)
                    .positionIndex(position++)
                    .build());
        }

        cache.setLastRefreshedOn(today);
        cache.setUpdatedAtUtc(Instant.now());
        return searchCacheRepository.save(cache);
    }

    @Transactional(readOnly = true)
    protected List<YouTubeVideoDto> loadFromCache(YouTubeSearchCache cache, int limit) {
        List<YouTubeSearchCacheItem> items = cacheItemRepository.findByCacheOrderByPositionIndexAsc(cache);
        List<YouTubeVideoDto> out = new ArrayList<>();

        for (YouTubeSearchCacheItem item : items) {
            if (out.size() >= limit)
                break;
            YouTubeVideoCache v = item.getVideo();
            out.add(YouTubeVideoDto.builder()
                    .videoId(v.getVideoId())
                    .title(v.getTitle())
                    .channelTitle(v.getChannelTitle())
                    .description(v.getDescription())
                    .thumbnailUrl(v.getThumbnailUrl())
                    .publishedAt(v.getPublishedAt())
                    .iframeUrl(v.getIframeUrl())
                    .categoryId(v.getCategoryId())
                    .build());
        }
        return out;
    }

    @Transactional
    protected YouTubeVideoCache upsertVideo(YouTubeVideoDto dto) {
        YouTubeVideoCache entity = videoCacheRepository.findByVideoId(dto.getVideoId())
                .orElseGet(YouTubeVideoCache::new);

        entity.setVideoId(dto.getVideoId());
        entity.setTitle(dto.getTitle());
        entity.setChannelTitle(dto.getChannelTitle());
        entity.setDescription(dto.getDescription());
        entity.setThumbnailUrl(dto.getThumbnailUrl());
        entity.setPublishedAt(dto.getPublishedAt());
        entity.setIframeUrl(dto.getIframeUrl());
        entity.setCategoryId(dto.getCategoryId());
        entity.setUpdatedAtUtc(Instant.now());
        return videoCacheRepository.save(entity);
    }

    private YouTubeSearchResponse buildSingleVideoResponse(String originalInput, String videoId) {
        YouTubeVideoCache cached = videoCacheRepository.findByVideoId(videoId).orElse(null);
        if (cached == null || isStale(cached.getUpdatedAtUtc())) {
            String rawJson = youTubeClient.fetchVideoById(videoId);
            YouTubeVideoDto dto = mapVideoById(rawJson, videoId);
            if (dto == null) {
                throw new IllegalArgumentException("Invalid YouTube URL or video not found");
            }
            cached = upsertVideo(dto);
        }

        YouTubeVideoDto result = YouTubeVideoDto.builder()
                .videoId(cached.getVideoId())
                .title(cached.getTitle())
                .channelTitle(cached.getChannelTitle())
                .description(cached.getDescription())
                .thumbnailUrl(cached.getThumbnailUrl())
                .publishedAt(cached.getPublishedAt())
                .iframeUrl(cached.getIframeUrl())
                .categoryId(cached.getCategoryId())
                .build();

        return YouTubeSearchResponse.builder()
                .query(originalInput)
                .count(1)
                .videos(List.of(result))
                .build();
    }

    private YouTubeVideoCache fetchAndCacheVideo(String videoId) {
        String rawJson = youTubeClient.fetchVideoById(videoId);
        YouTubeVideoDto dto = mapVideoById(rawJson, videoId);
        if (dto == null) {
            return null;
        }
        return upsertVideo(dto);
    }

    private boolean isStale(Instant updatedAtUtc) {
        if (updatedAtUtc == null)
            return true;
        LocalDate updatedDay = updatedAtUtc.atZone(java.time.ZoneOffset.UTC).toLocalDate();
        return !updatedDay.isEqual(LocalDate.now());
    }

    private String normalizeQuery(String query) {
        return query.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private int normalizeLimit(int requestedLimit) {
        int requested = requestedLimit <= 0 ? DEFAULT_LIMIT : requestedLimit;
        return Math.min(requested, MAX_VIDEOS_PER_QUERY);
    }

    private List<YouTubeVideoDto> mapSearchVideos(String rawJson) {
        List<YouTubeVideoDto> videos = new ArrayList<>();
        JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse YouTube search response", e);
        }
        if (root == null || root.path("items").isMissingNode()) {
            return videos;
        }

        List<String> videoIdsList = new ArrayList<>();
        for (JsonNode item : root.path("items")) {
            String videoId = item.path("id").path("videoId").asText("");
            if (videoId.isBlank()) continue;
            
            JsonNode snippet = item.path("snippet");
            String title = snippet.path("title").asText("");
            String channelTitle = snippet.path("channelTitle").asText("");
            String description = snippet.path("description").asText("");
            String publishedAt = snippet.path("publishedAt").asText("");
            String thumbnailUrl = resolveThumbnail(snippet.path("thumbnails"));

            videos.add(YouTubeVideoDto.builder()
                    .videoId(videoId)
                    .title(title)
                    .channelTitle(channelTitle)
                    .description(description)
                    .publishedAt(publishedAt)
                    .thumbnailUrl(thumbnailUrl)
                    .iframeUrl("https://www.youtube.com/embed/" + videoId)
                    .categoryId("") // Will be enriched
                    .build());
            
            videoIdsList.add(videoId);
            if (videos.size() >= MAX_VIDEOS_PER_QUERY) break;
        }

        // Enrich with Category IDs (which are NOT returned by the /search endpoint)
        if (!videoIdsList.isEmpty()) {
            enrichVideoCategoryIds(videos, videoIdsList);
        }

        return videos;
    }

    private void enrichVideoCategoryIds(List<YouTubeVideoDto> videos, List<String> videoIds) {
        try {
            String csv = String.join(",", videoIds);
            String rawDetailsJson = youTubeClient.fetchVideosByIds(csv);
            JsonNode root = objectMapper.readTree(rawDetailsJson);
            
            // Build a map of videoId -> categoryId
            java.util.Map<String, String> categoryMap = new java.util.HashMap<>();
            if (root != null && !root.path("items").isMissingNode()) {
                for (JsonNode item : root.path("items")) {
                    String vId = item.path("id").asText("");
                    String catId = item.path("snippet").path("categoryId").asText("");
                    if (!vId.isBlank() && !catId.isBlank()) {
                        categoryMap.put(vId, catId);
                    }
                }
            }
            
            // Enrich the DTOs
            for (YouTubeVideoDto video : videos) {
                String catId = categoryMap.get(video.getVideoId());
                if (catId != null) {
                    video.setCategoryId(catId);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to enrich video category IDs: {}", e.getMessage());
        }
    }

    private YouTubeVideoDto mapVideoById(String rawJson, String fallbackVideoId) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse YouTube video response", e);
        }

        JsonNode items = root.path("items");
        if (items.isMissingNode() || !items.isArray() || items.isEmpty())
            return null;

        JsonNode item = items.get(0);
        String videoId = item.path("id").asText(fallbackVideoId);
        JsonNode snippet = item.path("snippet");
        String title = snippet.path("title").asText("");
        String channelTitle = snippet.path("channelTitle").asText("");
        String description = snippet.path("description").asText("");
        String publishedAt = snippet.path("publishedAt").asText("");
        String thumbnailUrl = resolveThumbnail(snippet.path("thumbnails"));
        String categoryId = snippet.path("categoryId").asText("");

        return YouTubeVideoDto.builder()
                .videoId(videoId)
                .title(title)
                .channelTitle(channelTitle)
                .description(description)
                .publishedAt(publishedAt)
                .thumbnailUrl(thumbnailUrl)
                .iframeUrl("https://www.youtube.com/embed/" + videoId)
                .categoryId(categoryId)
                .build();
    }

    private Optional<String> extractVideoId(String input) {
        if (input == null || input.isBlank())
            return Optional.empty();

        String trimmed = input.trim();
        if (trimmed.matches("^[A-Za-z0-9_-]{11}$")) {
            return Optional.of(trimmed);
        }
        if (!(trimmed.contains("youtube.com") || trimmed.contains("youtu.be"))) {
            return Optional.empty();
        }

        try {
            String uri = trimmed.contains("://") ? trimmed : "https://" + trimmed;
            String query = UriComponentsBuilder.fromUriString(uri).build().getQuery();
            if (query != null) {
                Matcher m = YT_WATCH_PATTERN.matcher("?" + query);
                if (m.find())
                    return Optional.of(m.group(1));
            }
        } catch (Exception ignored) {
        }

        Matcher shortMatcher = YT_SHORT_PATTERN.matcher(trimmed);
        if (shortMatcher.find())
            return Optional.of(shortMatcher.group(1));

        Matcher shortsMatcher = YT_SHORTS_PATTERN.matcher(trimmed);
        if (shortsMatcher.find())
            return Optional.of(shortsMatcher.group(1));

        return Optional.empty();
    }

    private String resolveThumbnail(JsonNode thumbnails) {
        if (thumbnails == null || thumbnails.isMissingNode())
            return "";
        String[] order = { "high", "medium", "default" };
        for (String key : order) {
            String url = thumbnails.path(key).path("url").asText("");
            if (!url.isBlank())
                return url;
        }
        return "";
    }

}
