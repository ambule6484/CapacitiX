
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
public class LearningPathResponse {

    /*
     * =========================================================
     * EMPLOYEE INFORMATION
     * =========================================================
     */
    private String employeeName;

    private String employeeRole;


    /*
     * =========================================================
     * SUMMARY OF SKILL-GAP ANALYSIS
     * =========================================================
     */
    private int totalSkillGaps;

    private int highPriorityGaps;

    private int mediumPriorityGaps;


    /*
     * =========================================================
     * AI-GENERATED LEARNING PATH
     * =========================================================
     *
     * IMPORTANT:
     *
     * This response object must use the DTO LearningPathItem,
     * NOT the entity LearningPathItem.
     *
     * Both classes currently have the same simple name:
     *
     * com.capacitix.dto.LearningPathItem
     * com.capacitix.entity.LearningPathItem
     *
     * Since this class is already inside the dto package,
     * LearningPathItem automatically refers to:
     *
     * com.capacitix.dto.LearningPathItem
     */
    @Builder.Default
    private List<LearningPathItem> learningPath =
            new ArrayList<>();


    /*
     * =========================================================
     * OVERALL AI RECOMMENDATION
     * =========================================================
     */
    private String overallRecommendation;


    /*
     * =========================================================
     * EXPECTED OUTCOME
     * =========================================================
     */
    private String expectedOutcome;
}