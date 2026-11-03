
package com.capacitix.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO used to transfer personalized learning-path data
 * between the backend and employee/training UI.
 *
 * Keeps entity objects away from the controller response.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearningPathDTO {

    // =========================================================
    // LEARNING PATH DETAILS
    // =========================================================

    private Long id;

    private Long employeeId;

    private String employeeName;

    private String employeeEmail;

    private String employeeRole;

    private String title;

    private String description;

    /**
     * Skill gaps identified for this employee.
     *
     * Example:
     * Java, Spring Boot, SQL
     */
    private String skillGaps;

    /**
     * ACTIVE / COMPLETED / PAUSED
     */
    private String status;

    /**
     * Overall learning-path progress.
     *
     * Range: 0 - 100
     */
    @Builder.Default
    private Integer progressPercentage = 0;


    // =========================================================
    // PATH STATISTICS
    // =========================================================

    @Builder.Default
    private Integer totalModules = 0;

    @Builder.Default
    private Integer completedModules = 0;

    @Builder.Default
    private Integer inProgressModules = 0;

    @Builder.Default
    private Integer notStartedModules = 0;


    // =========================================================
    // DATES
    // =========================================================

    private String createdAt;

    private String updatedAt;


    // =========================================================
    // LEARNING PATH ITEMS
    // =========================================================

    /**
     * Ordered personalized learning-path items.
     */
    @Builder.Default
    private List<LearningPathItemDTO> items = new ArrayList<>();


    // =========================================================
    // INNER DTO
    // =========================================================

    /**
     * Represents one item inside the personalized
     * learning path.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LearningPathItemDTO {

        // =====================================================
        // ITEM DETAILS
        // =====================================================

        private Long id;

        /**
         * Parent learning path ID.
         */
        private Long learningPathId;

        /**
         * Training module associated with this item.
         */
        private Long trainingModuleId;

        /**
         * Position inside the learning path.
         */
        private Integer sequenceOrder;


        // =====================================================
        // SKILL GAP
        // =====================================================

        /**
         * Skill that this item is intended to improve.
         */
        private String targetSkill;

        /**
         * Why this item was recommended.
         */
        private String recommendationReason;

        /**
         * LOW / MEDIUM / HIGH
         */
        private String priority;


        // =====================================================
        // ITEM STATUS
        // =====================================================

        /**
         * NOT_STARTED / IN_PROGRESS / COMPLETED
         */
        private String status;

        /**
         * Progress from 0 to 100.
         */
        @Builder.Default
        private Integer progressPercentage = 0;


        // =====================================================
        // TRAINING MODULE DATA
        // =====================================================

        private String moduleTitle;

        private String moduleDescription;

        private String skillName;

        /**
         * BEGINNER / INTERMEDIATE / ADVANCED
         */
        private String difficultyLevel;

        /**
         * VIDEO / ARTICLE / COURSE / DOCUMENT / PRACTICE
         */
        private String moduleType;

        /**
         * Training content.
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

        /**
         * Whether this is an employee-specific
         * personalized module.
         */
        @Builder.Default
        private Boolean personalized = false;

        /**
         * Whether the module is active.
         */
        @Builder.Default
        private Boolean active = true;


        // =====================================================
        // TRAINING PROGRESS
        // =====================================================

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
         * Assessment score for this training item.
         */
        private Double assessmentScore;

        /**
         * Employee feedback.
         */
        private String feedback;

        private String startedAt;

        private String completedAt;

        private String lastAccessedAt;
    }
}