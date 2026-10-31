
package com.capacitix.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "assessments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Assessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Basic information
    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    // Assessment type: SKILL, TECHNICAL, PRACTICAL
    @Column(nullable = false, length = 30)
    @Builder.Default
    private String type = "SKILL";

    // GENERATED, ASSIGNED, IN_PROGRESS, COMPLETED, PENDING
    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "GENERATED";

    // AI-generated assessment
    @Column(name = "ai_generated", nullable = false)
    @Builder.Default
    private Boolean aiGenerated = true;

    // Target role
    @Column(name = "target_role", length = 150)
    private String targetRole;

    // Question count
    @Column(name = "question_count", nullable = false)
    @Builder.Default
    private Integer questionCount = 0;

    // Optional employee association
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private User employee;

    // Score information
    @Column(nullable = false)
    @Builder.Default
    private Integer score = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer maxScore = 100;

    // Dates
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // Helper methods
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

    public boolean isCompleted() {
        return "COMPLETED".equalsIgnoreCase(status);
    }

    public boolean isAssigned() {
        return "ASSIGNED".equalsIgnoreCase(status);
    }

    public boolean isInProgress() {
        return "IN_PROGRESS".equalsIgnoreCase(status);
    }

    public boolean isAiGenerated() {
        return Boolean.TRUE.equals(aiGenerated);
    }
}
