
package com.capacitix.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningPathItem {

    /*
     * Skill/competency that needs improvement
     *
     * Example:
     * Java
     * Spring Boot
     * SQL
     * REST API
     */
    private String skillName;

    /*
     * Priority decided from the skill-gap analysis.
     *
     * Example:
     * HIGH
     * MEDIUM
     * LOW
     */
    private String priority;

    /*
     * Employee's current assessment score.
     *
     * Example:
     * 42.0
     */
    private Double currentScore;

    /*
     * Why this skill was included in
     * the learning path.
     */
    private String gapReason;

    /*
     * Topics that the employee should learn.
     *
     * Example:
     * OOP
     * Collections
     * Exception Handling
     * Streams
     */
    @Builder.Default
    private List<String> topics = new ArrayList<>();

    /*
     * Practical task recommended by AI.
     *
     * Example:
     * Build a Java Employee Management System.
     */
    private String practicalTask;

    /*
     * Estimated time required to learn
     * this skill.
     *
     * Example:
     * 7 days
     */
    private String estimatedDuration;

    /*
     * Expected result after completing
     * this learning item.
     */
    private String expectedOutcome;

    /*
     * Learning order.
     *
     * 1 = learn first
     * 2 = learn second
     * 3 = learn third
     */
    private Integer learningOrder;
}