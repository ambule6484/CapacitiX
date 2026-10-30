package com.capacitix.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "assessment_assignments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_assessment_employee",
                        columnNames = {
                                "assessment_id",
                                "employee_id"
                        }
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssessmentAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /*
     * =========================================================
     * ASSESSMENT
     * =========================================================
     */

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "assessment_id",
            nullable = false
    )
    private Assessment assessment;


    /*
     * =========================================================
     * EMPLOYEE
     * =========================================================
     */

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "employee_id",
            nullable = false
    )
    private User employee;


    /*
     * =========================================================
     * TRAINER
     * =========================================================
     */

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "trainer_id",
            nullable = false
    )
    private User trainer;


    /*
     * =========================================================
     * ASSIGNMENT STATUS
     * =========================================================
     *
     * ASSIGNED
     * IN_PROGRESS
     * COMPLETED
     * CANCELLED
     */

    @Column(
            nullable = false,
            length = 30
    )
    @Builder.Default
    private String status = "ASSIGNED";


    /*
     * =========================================================
     * DATES
     * =========================================================
     */

    @Column(
            name = "assigned_at",
            nullable = false
    )
    @Builder.Default
    private LocalDateTime assignedAt = LocalDateTime.now();

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;


    /*
     * =========================================================
     * SCORE
     * =========================================================
     */

    @Column(nullable = false)
    @Builder.Default
    private Integer score = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer maxScore = 0;


    /*
     * =========================================================
     * HELPER METHODS
     * =========================================================
     */

    public boolean isAssigned() {
        return "ASSIGNED".equalsIgnoreCase(status);
    }


    public boolean isInProgress() {
        return "IN_PROGRESS".equalsIgnoreCase(status);
    }


    public boolean isCompleted() {
        return "COMPLETED".equalsIgnoreCase(status);
    }


    public boolean isCancelled() {
        return "CANCELLED".equalsIgnoreCase(status);
    }


    /*
     * =========================================================
     * PERCENTAGE
     * =========================================================
     */

    public int getPercentage() {

        if (maxScore == null || maxScore <= 0) {
            return 0;
        }

        if (score == null || score <= 0) {
            return 0;
        }

        return (int) Math.round(
                ((double) score / maxScore) * 100
        );
    }
}