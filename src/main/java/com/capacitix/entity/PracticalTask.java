
package com.capacitix.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "practical_tasks",
        indexes = {
                @Index(
                        name = "idx_practical_task_employee",
                        columnList = "employee_id"
                ),
                @Index(
                        name = "idx_practical_task_learning_path_item",
                        columnList = "learning_path_item_id"
                ),
                @Index(
                        name = "idx_practical_task_training_module",
                        columnList = "training_module_id"
                ),
                @Index(
                        name = "idx_practical_task_skill",
                        columnList = "skill_name"
                ),
                @Index(
                        name = "idx_practical_task_status",
                        columnList = "status"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {
        "employee",
        "learningPathItem",
        "trainingModule"
})
public class PracticalTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "employee_id",
            nullable = false
    )
    private User employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "learning_path_item_id",
            nullable = false
    )
    private LearningPathItem learningPathItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "training_module_id"
    )
    private TrainingModule trainingModule;

    @Column(
            nullable = false,
            length = 250
    )
    private String title;

    @Column(
            name = "skill_name",
            nullable = false,
            length = 150
    )
    private String skillName;

    @Column(
            columnDefinition = "LONGTEXT",
            nullable = false
    )
    private String description;

    @Column(
            name = "expected_outcome",
            columnDefinition = "TEXT"
    )
    private String expectedOutcome;

    @Column(
            columnDefinition = "LONGTEXT"
    )
    private String requirements;

    @Column(length = 150)
    private String technology;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    @Builder.Default
    private DifficultyLevel difficulty = DifficultyLevel.BEGINNER;

    @Column(
            name = "estimated_minutes",
            nullable = false
    )
    @Builder.Default
    private Integer estimatedMinutes = 30;

    @Column(name = "skill_score")
    private Integer skillScore;

    @Column(
            name = "required_skill_level",
            length = 50
    )
    private String requiredSkillLevel;

    @Column(
            name = "current_skill_level",
            length = 50
    )
    private String currentSkillLevel;

    @Column(name = "gap_percentage")
    private Integer gapPercentage;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    @Builder.Default
    private Priority priority = Priority.MEDIUM;

    /*
     * IMPORTANT:
     *
     * COMPLETED is only set after trainer approval.
     */
    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 30
    )
    @Builder.Default
    private TaskStatus status = TaskStatus.PENDING;

    /*
     * 100% is reserved for trainer-approved completion.
     *
     * SUBMITTED = maximum 99%.
     */
    @Column(
            nullable = false
    )
    @Builder.Default
    private Integer progressPercentage = 0;

    @Column(
            name = "ai_generated",
            nullable = false
    )
    @Builder.Default
    private Boolean aiGenerated = true;

    @Column(name = "task_order")
    private Integer taskOrder;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;


    // ============================================================
    // JPA LIFECYCLE
    // ============================================================

    @PrePersist
    protected void onCreate() {

        LocalDateTime now = LocalDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }

        if (difficulty == null) {
            difficulty = DifficultyLevel.BEGINNER;
        }

        if (priority == null) {
            priority = Priority.MEDIUM;
        }

        if (status == null) {
            status = TaskStatus.PENDING;
        }

        if (progressPercentage == null) {
            progressPercentage = 0;
        }

        if (estimatedMinutes == null || estimatedMinutes <= 0) {
            estimatedMinutes = 30;
        }

        if (aiGenerated == null) {
            aiGenerated = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }


    // ============================================================
    // EMPLOYEE TASK ACTIONS
    // ============================================================

    /**
     * Employee starts the practical task.
     */
    public void startTask() {

        if (status == TaskStatus.COMPLETED) {
            return;
        }

        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }

        status = TaskStatus.IN_PROGRESS;

        if (progressPercentage == null || progressPercentage <= 0) {
            progressPercentage = 1;
        }
    }


    /**
     * Update employee working progress.
     *
     * Maximum = 99%.
     *
     * 100% is reserved for trainer-approved completion.
     */
    public void updateProgress(Integer progress) {

        if (progress == null) {
            return;
        }

        if (status == TaskStatus.COMPLETED) {
            return;
        }

        int safeProgress = Math.max(
                0,
                Math.min(99, progress)
        );

        this.progressPercentage = safeProgress;

        if (safeProgress > 0) {

            if (startedAt == null) {
                startedAt = LocalDateTime.now();
            }

            status = TaskStatus.IN_PROGRESS;
        }

        if (safeProgress == 0) {
            status = TaskStatus.PENDING;
        }
    }


    /**
     * Employee submits the practical task.
     *
     * IMPORTANT:
     *
     * SUBMITTED != COMPLETED
     *
     * Trainer must approve before completion.
     */
    public void markSubmitted() {

        if (status == TaskStatus.COMPLETED) {
            return;
        }

        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }

        status = TaskStatus.SUBMITTED;

        /*
         * Do NOT set 100 here.
         *
         * Trainer approval is required.
         */
        if (progressPercentage == null || progressPercentage < 1) {
            progressPercentage = 99;
        }

        completedAt = null;
    }


    /**
     * Trainer requests improvement.
     */
    public void markNeedsImprovement() {

        if (status == TaskStatus.COMPLETED) {
            return;
        }

        status = TaskStatus.NEEDS_IMPROVEMENT;

        completedAt = null;

        /*
         * Task is still incomplete.
         */
        if (progressPercentage == null || progressPercentage > 99) {
            progressPercentage = 99;
        }
    }


    /**
     * ONLY trainer approval should call this method.
     *
     * This officially completes the practical task.
     */
    public void markCompleted() {

        status = TaskStatus.COMPLETED;

        progressPercentage = 100;

        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }

        completedAt = LocalDateTime.now();
    }


    // ============================================================
    // STATUS HELPERS
    // ============================================================

    public boolean isCompleted() {
        return status == TaskStatus.COMPLETED;
    }

    public boolean isSubmitted() {
        return status == TaskStatus.SUBMITTED;
    }

    public boolean needsImprovement() {
        return status == TaskStatus.NEEDS_IMPROVEMENT;
    }

    public boolean isPendingReview() {
        return status == TaskStatus.SUBMITTED;
    }


    // ============================================================
    // ENUMS
    // ============================================================

    public enum DifficultyLevel {
        BEGINNER,
        INTERMEDIATE,
        ADVANCED
    }

    public enum Priority {
        LOW,
        MEDIUM,
        HIGH
    }

    public enum TaskStatus {
        PENDING,
        IN_PROGRESS,
        SUBMITTED,
        NEEDS_IMPROVEMENT,
        COMPLETED
    }
}