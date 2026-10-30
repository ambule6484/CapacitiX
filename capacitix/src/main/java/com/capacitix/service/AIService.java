
package com.capacitix.service;

import com.capacitix.dto.LearningPathResponse;
import com.capacitix.entity.User;

public interface AIService {

    /**
     * Generates a personalized learning path using AI.
     *
     * The AI should consider:
     * - Employee role
     * - Assessment performance
     * - Current skill levels
     * - Identified skill gaps
     *
     * @param employee logged-in employee
     * @param skillGaps skill gaps identified from assessment
     * @return AI-generated personalized learning path
     */
    LearningPathResponse generateLearningPath(
            User employee,
            Object skillGaps
    );
}