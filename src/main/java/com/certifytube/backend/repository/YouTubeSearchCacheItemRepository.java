package com.certifytube.backend.repository;

import com.certifytube.backend.model.YouTubeSearchCache;
import com.certifytube.backend.model.YouTubeSearchCacheItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface YouTubeSearchCacheItemRepository extends JpaRepository<YouTubeSearchCacheItem, Long> {
    List<YouTubeSearchCacheItem> findByCacheOrderByPositionIndexAsc(YouTubeSearchCache cache);
    void deleteByCache(YouTubeSearchCache cache);


}
