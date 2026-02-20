package com.certifytube.backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "quiz_questions", indexes = {
        @Index(name = "idx_quiz_questions_quiz", columnList = "quiz_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @Column(name = "question_uid", nullable = false, length = 36)
    private String questionUid;

    @Column(name = "position_index", nullable = false)
    private Integer positionIndex;

    @Column(name = "question_type", nullable = false, length = 32)
    private String questionType;

    @Lob
    @Column(name = "question_text", nullable = false, columnDefinition = "LONGTEXT")
    private String questionText;

    @Lob
    @Column(name = "options_json", columnDefinition = "LONGTEXT")
    private String optionsJson;

    @Lob
    @Column(name = "correct_answer", nullable = false, columnDefinition = "LONGTEXT")
    private String correctAnswer;

    @Lob
    @Column(name = "explanation_text", columnDefinition = "LONGTEXT")
    private String explanationText;
}
