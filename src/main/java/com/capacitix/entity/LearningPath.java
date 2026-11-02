package com.capacitix.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "learning_paths",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_learning_path_assessment_assignment",
                        columnNames = "assessment_assignment_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearningPath {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Employee for whom this learning path was generated.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "employee_id",
            nullable = false
    )
    private User employee;

    /**
     * Completed assessment assignment that generated
     * this personalized learning path.
     *
     * One assessment assignment can generate
     * only one learning path.
     *
     * Nullable because manually created learning paths
     * may not have an assessment assignment.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "assessment_assignment_id",
            unique = true,
            nullable = true
    )
    private AssessmentAssignment assessmentAssignment;

    /**
     * Title of the personalized learning path.
     */
    @Column(
            nullable = false,
            length = 200
    )
    private String title;

    /**
     * AI-generated description.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Skill gaps used to generate this learning path.
     *
     * Example:
     * Java OOP, Collections, REST API
     */
    @Column(columnDefinition = "TEXT")
    private String skillGaps;

    /**
     * Current learning path status.
     */
    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 30
    )
    @Builder.Default
    private LearningPathStatus status =
            LearningPathStatus.ACTIVE;

    /**
     * Overall progress from 0 to 100.
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer progressPercentage = 0;

    /**
     * Creation timestamp.
     */
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt =
            LocalDateTime.now();

    /**
     * Last update timestamp.
     */
    private LocalDateTime updatedAt;

    /**
     * Training items belonging to this learning path.
     */
    @OneToMany(
            mappedBy = "learningPath",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("sequenceOrder ASC")
    @Builder.Default
    private List<LearningPathItem> items =
            new ArrayList<>();

    /**
     * Automatically set timestamps.
     */
    @PrePersist
    protected void onCreate() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {

        updatedAt = LocalDateTime.now();
    }

    /**
     * Learning path status.
     */
    public enum LearningPathStatus {

        ACTIVE,

        COMPLETED,

        PAUSED
    }
}