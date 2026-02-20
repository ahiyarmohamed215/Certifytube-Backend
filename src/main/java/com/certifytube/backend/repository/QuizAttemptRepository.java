package com.certifytube.backend.repository;

import com.certifytube.backend.model.Quiz;
import com.certifytube.backend.model.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
    Optional<QuizAttempt> findTopByQuizAndUserIdOrderByCreatedAtUtcDesc(Quiz quiz, Long userId);

    @Query("""
            select count(qa)
            from QuizAttempt qa
            join qa.quiz q
            where qa.userId = :userId
              and q.sessionId = :sessionId
              and qa.createdAtUtc >= :fromTime
              and qa.passedFlag = false
            """)
    long countFailedAttemptsForSessionSince(
            @Param("userId") Long userId,
            @Param("sessionId") String sessionId,
            @Param("fromTime") Instant fromTime
    );
}
