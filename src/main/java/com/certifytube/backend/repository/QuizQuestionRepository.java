package com.certifytube.backend.repository;

import com.certifytube.backend.model.Quiz;
import com.certifytube.backend.model.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {
    List<QuizQuestion> findByQuizOrderByPositionIndexAsc(Quiz quiz);
    void deleteByQuizIn(List<Quiz> quizzes);
}
