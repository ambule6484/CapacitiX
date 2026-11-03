package com.capacitix.service;

import com.capacitix.dto.LearningPathResponse;
import com.capacitix.entity.User;

public interface LearningPathPdfService {

    /**
     * Generates a personalized Learning Path PDF
     * for the given employee.
     *
     * @param employee logged-in employee
     * @param learningPath AI-generated learning path
     * @return generated PDF as byte array
     */
    byte[] generateLearningPathPdf(
            User employee,
            LearningPathResponse learningPath
    );
}