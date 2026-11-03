package com.capacitix.service;

import com.capacitix.dto.LearningPathResponse;
import com.capacitix.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI implementation for personalized learning-path generation.
 *
 * This class is responsible only for AI generation.
 *
 * Database persistence is handled by LearningPathServiceImpl
 * or the service/controller responsible for converting the
 * AI response into LearningPath and LearningPathItem entities.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AILearningPathGeneratorImpl
        implements AILearningPathGenerator {

    private final AIService aiService;

    /**
     * Generates a personalized learning path based on
     * the employee's identified skill gaps.
     */
    @Override
    public LearningPathResponse generateLearningPath(
            User employee,
            SkillAnalysisService.SkillAnalysisResult analysis
    ) {

        // -----------------------------------------------------
        // Validate employee
        // -----------------------------------------------------

        if (employee == null) {
            throw new IllegalArgumentException(
                    "Employee cannot be null."
            );
        }

        // -----------------------------------------------------
        // Validate analysis
        // -----------------------------------------------------

        if (analysis == null) {
            throw new IllegalArgumentException(
                    "Skill analysis cannot be null."
            );
        }

        // -----------------------------------------------------
        // No skill gaps
        // -----------------------------------------------------

        if (analysis.skillGaps() == null
                || analysis.skillGaps().isEmpty()) {

            return LearningPathResponse.builder()
                    .employeeName(employee.getFullName())
                    .employeeRole(employee.getDesignation())
                    .totalSkillGaps(0)
                    .highPriorityGaps(0)
                    .mediumPriorityGaps(0)
                    .learningPath(java.util.List.of())
                    .overallRecommendation(
                            "No skill gaps were identified "
                                    + "from the completed assessment."
                    )
                    .expectedOutcome(
                            "Continue practicing your existing "
                                    + "skills and maintain your "
                                    + "current competency level."
                    )
                    .build();
        }

        // -----------------------------------------------------
        // Generate using AI /
        // -----------------------------------------------------

        LearningPathResponse response =
                aiService.generateLearningPath(
                        employee,
                        analysis
                );

        // -----------------------------------------------------
        // Protect against empty response
        // -----------------------------------------------------

        if (response == null) {
            throw new IllegalStateException(
                    "AI learning-path generation returned no response."
            );
        }

        return response;
    }
}