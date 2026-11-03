
package com.capacitix.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "assessment_questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssessmentQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Every question belongs to one assessment
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    private Assessment assessment;

    // Competency associated with this question
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competency_id", nullable = false)
    private Competency competency;

    // Question information
    @Column(nullable = false, length = 2000)
    private String questionText;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String questionType = "MCQ";

    // Options
    @Column(length = 1000)
    private String optionA;

    @Column(length = 1000)
    private String optionB;

    @Column(length = 1000)
    private String optionC;

    @Column(length = 1000)
    private String optionD;

    // Correct answer
    @Column(name = "correct_answer", nullable = false, length = 10)
    private String correctAnswer;

    // Difficulty
    @Column(length = 30)
    @Builder.Default
    private String difficulty = "INTERMEDIATE";

    // Marks
    @Column(nullable = false)
    @Builder.Default
    private Integer marks = 1;

    // AI explanation
    @Column(length = 2000)
    private String explanation;

    // Question order
    @Column(name = "question_order", nullable = false)
    @Builder.Default
    private Integer questionOrder = 1;

    // Active status
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    // Answer validation
    public boolean isCorrectAnswer(String answer) {

        if (answer == null || correctAnswer == null) {
            return false;
        }

        return correctAnswer.trim()
                .equalsIgnoreCase(answer.trim());
    }
}
