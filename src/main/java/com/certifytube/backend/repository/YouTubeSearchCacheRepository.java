package com.certifytube.backend.repository;

import com.certifytube.backend.model.YouTubeSearchCache;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;


public interface YouTubeSearchCacheRepository extends JpaRepository<YouTubeSearchCache, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<YouTubeSearchCache> findByQueryText(String queryText);
}
