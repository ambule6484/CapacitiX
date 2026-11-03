package com.capacitix.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "assessment_responses",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_assignment_question",
                        columnNames = {
                                "assignment_id",
                                "question_id"
                        }
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssessmentResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /*
     * =========================================================
     * ASSESSMENT ASSIGNMENT
     * =========================================================
     *
     * Identifies which employee's assessment attempt
     * this response belongs to.
     */

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "assignment_id",
            nullable = false
    )
    private AssessmentAssignment assignment;


    /*
     * =========================================================
     * QUESTION
     * =========================================================
     *
     * The exact question answered by the employee.
     */

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "question_id",
            nullable = false
    )
    private AssessmentQuestion question;


    /*
     * =========================================================
     * EMPLOYEE ANSWER
     * =========================================================
     *
     * Example:
     * A
     * B
     * C
     * D
     */

    @Column(
            name = "selected_answer",
            length = 1000
    )
    private String selectedAnswer;


    /*
     * =========================================================
     * CORRECTNESS
     * =========================================================
     *
     * Java calculates this.
     * AI is NOT used to determine correctness.
     */

    @Column(nullable = false)
    @Builder.Default
    private Boolean correct = false;


    /*
     * =========================================================
     * MARKS
     * =========================================================
     */

    @Column(
            name = "marks_awarded",
            nullable = false
    )
    @Builder.Default
    private Integer marksAwarded = 0;


    @Column(
            name = "max_marks",
            nullable = false
    )
    @Builder.Default
    private Integer maxMarks = 1;


    /*
     * =========================================================
     * ANSWERED TIME
     * =========================================================
     */

    @Column(
            name = "answered_at",
            nullable = false
    )
    @Builder.Default
    private LocalDateTime answeredAt =
            LocalDateTime.now();


    /*
     * =========================================================
     * HELPER METHOD
     * =========================================================
     *
     * Checks the employee answer using the stored
     * correct answer from AssessmentQuestion.
     */

    public void evaluateAnswer() {

        if (question == null) {
            this.correct = false;
            this.marksAwarded = 0;
            return;
        }

        boolean isCorrect =
                question.isCorrectAnswer(selectedAnswer);

        this.correct = isCorrect;

        if (isCorrect) {
            this.marksAwarded =
                    question.getMarks() != null
                            ? question.getMarks()
                            : 0;
        } else {
            this.marksAwarded = 0;
        }

        this.maxMarks =
                question.getMarks() != null
                        ? question.getMarks()
                        : 1;
    }


    public boolean isCorrect() {

        return Boolean.TRUE.equals(correct);
    }
}