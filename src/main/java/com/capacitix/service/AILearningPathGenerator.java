package com.capacitix.service;

import com.capacitix.dto.LearningPathResponse;
import com.capacitix.entity.User;

/**
 * Generates a personalized learning path using AI.
 *
 * Flow:
 *
 * Assessment
 *      ↓
 * Skill Gap Analysis
 *      ↓
 * AILearningPathGenerator
 *      ↓
 * LearningPathResponse
 *      ↓
 * LearningPathService
 *      ↓
 * LearningPath + LearningPathItems + TrainingModules
 */
public interface AILearningPathGenerator {

    /**
     * Generates a personalized learning path
     * for the given employee.
     *
     * @param employee employee for whom the path is generated
     * @param analysis skill-gap analysis result
     * @return AI-generated learning path response
     */
    LearningPathResponse generateLearningPath(
            User employee,
            SkillAnalysisService.SkillAnalysisResult analysis
    );
}