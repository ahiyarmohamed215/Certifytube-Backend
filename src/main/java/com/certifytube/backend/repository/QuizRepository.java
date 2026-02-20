package com.certifytube.backend.repository;

import com.certifytube.backend.model.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuizRepository extends JpaRepository<Quiz, String> {
    Optional<Quiz> findTopBySessionIdAndUserIdOrderByCreatedAtUtcDesc(String sessionId, Long userId);
}
