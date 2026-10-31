
package com.capacitix.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningRecommendation {

    /*
     * Skill for which this recommendation is generated.
     *
     * Example:
     * Java
     * Spring Boot
     * SQL
     */
    private String skillName;

    /*
     * Recommended topic or concept.
     *
     * Example:
     * Java Collections
     * Spring Data JPA
     * SQL Joins
     */
    private String topic;

    /*
     * Type of recommendation.
     *
     * Examples:
     * COURSE
     * VIDEO
     * DOCUMENTATION
     * PRACTICAL_TASK
     * QUIZ
     */
    private String type;

    /*
     * Short explanation of what the employee
     * should learn.
     */
    private String description;

    /*
     * Why AI recommends this topic based
     * on the employee's assessment result.
     */
    private String reason;

    /*
     * Priority of this recommendation.
     *
     * HIGH
     * MEDIUM
     * LOW
     */
    private String priority;

    /*
     * Estimated time required.
     *
     * Example:
     * 2 hours
     * 3 days
     */
    private String estimatedDuration;

    /*
     * Recommended learning order.
     */
    private Integer learningOrder;

    /*
     * Optional learning/resource URL.
     *
     * Can remain null if the organization
     * provides the resource internally.
     */
    private String resourceUrl;

    /*
     * Indicates whether the recommendation
     * is completed by the employee.
     */
    @Builder.Default
    private boolean completed = false;
}
