
package com.capacitix.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * DTO used to transfer training-module data
 * between the backend and frontend.
 *
 * Keeps the TrainingModule entity separate from
 * the UI layer.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingModuleDTO {

    // =========================================================
    // BASIC DETAILS
    // =========================================================

    private Long id;

    /**
     * Training module title.
     */
    private String title;

    /**
     * Short explanation of the training module.
     */
    private String description;

    /**
     * Skill this module improves.
     */
    private String skillName;


    // =========================================================
    // MODULE CLASSIFICATION
    // =========================================================

    /**
     * BEGINNER / INTERMEDIATE / ADVANCED
     */
    private String difficultyLevel;

    /**
     * VIDEO / ARTICLE / COURSE / DOCUMENT / PRACTICE
     */
    private String moduleType;


    // =========================================================
    // TRAINING CONTENT
    // =========================================================

    /**
     * Main training content.
     *
     * Can contain AI-generated personalized
     * learning material.
     */
    private String content;

    /**
     * Optional external resource URL.
     */
    private String resourceUrl;

    /**
     * Estimated completion time in minutes.
     */
    @Builder.Default
    private Integer estimatedMinutes = 30;


    // =========================================================
    // MODULE STATUS
    // =========================================================

    /**
     * Whether the module is currently available.
     */
    @Builder.Default
    private Boolean active = true;

    /**
     * Whether this module was generated specifically
     * for an employee's personalized learning path.
     */
    @Builder.Default
    private Boolean personalized = false;


    // =========================================================
    // AUDIT INFORMATION
    // =========================================================

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;


    // =========================================================
    // LEARNING PATH INFORMATION
    // =========================================================

    /**
     * Parent learning path ID.
     */
    private Long learningPathId;

    /**
     * Learning path item associated with this module.
     */
    private Long learningPathItemId;

    /**
     * Position of this item in the learning path.
     */
    private Integer sequenceOrder;

    /**
     * Skill gap that caused this module
     * to be recommended.
     */
    private String targetSkill;

    /**
     * Explanation of why this module
     * was recommended.
     */
    private String recommendationReason;

    /**
     * LOW / MEDIUM / HIGH
     */
    private String priority;


    // =========================================================
    // EMPLOYEE PROGRESS
    // =========================================================

    /**
     * Progress from 0 to 100.
     */
    @Builder.Default
    private Integer progressPercentage = 0;

    /**
     * NOT_STARTED / IN_PROGRESS / COMPLETED
     */
    private String progressStatus;

    /**
     * Whether the employee has started this module.
     */
    @Builder.Default
    private Boolean started = false;

    /**
     * Total time spent by the employee.
     */
    @Builder.Default
    private Integer timeSpentMinutes = 0;

    /**
     * Assessment score associated with this module.
     */
    private Double assessmentScore;

    /**
     * Employee feedback.
     */
    private String feedback;

    /**
     * When the employee started the module.
     */
    private LocalDateTime startedAt;

    /**
     * When the employee completed the module.
     */
    private LocalDateTime completedAt;

    /**
     * Last time the employee accessed the module.
     */
    private LocalDateTime lastAccessedAt;
}