
package com.capacitix.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "employee_training_progress",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {"employee_id", "learning_path_item_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeTrainingProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Employee completing the training.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private User employee;

    /**
     * Learning path item assigned to the employee.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learning_path_item_id", nullable = false)
    private LearningPathItem learningPathItem;

    /**
     * Current progress of the training module.
     *
     * Range: 0 - 100
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer progressPercentage = 0;

    /**
     * Current training status.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private TrainingStatus status = TrainingStatus.NOT_STARTED;

    /**
     * Whether the employee has started the module.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean started = false;

    /**
     * Date and time when the employee started
     * the training module.
     */
    private LocalDateTime startedAt;

    /**
     * Date and time when the employee completed
     * the training module.
     */
    private LocalDateTime completedAt;

    /**
     * Last time the employee accessed the module.
     */
    private LocalDateTime lastAccessedAt;

    /**
     * Total time spent on this training module
     * in minutes.
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer timeSpentMinutes = 0;

    /**
     * Employee's score after completing
     * the training assessment, if available.
     */
    private Double assessmentScore;

    /**
     * Optional employee feedback.
     */
    @Column(columnDefinition = "TEXT")
    private String feedback;

    /**
     * Record creation time.
     */
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Last update time.
     */
    private LocalDateTime updatedAt;

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
     * Training status.
     */
    public enum TrainingStatus {

        NOT_STARTED,

        IN_PROGRESS,

        COMPLETED
    }
}