
package com.capacitix.service;

import com.capacitix.dto.LearningPathResponse;
import com.capacitix.entity.User;

public interface AIService {

    /**
     * Generates a personalized learning path using
     * actual employee assessment skill-gap data.
     *
     * @param employee logged-in employee
     * @param skillGaps identified skill gaps
     * @return personalized learning path
     */
    LearningPathResponse generateLearningPath(
            User employee,
            Object skillGaps
    );
}
