package com.certifytube.backend.repository;

import com.certifytube.backend.model.YouTubeVideoCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface YouTubeVideoCacheRepository extends JpaRepository<YouTubeVideoCache, Long> {
    Optional<YouTubeVideoCache> findByVideoId(String videoId);
}
