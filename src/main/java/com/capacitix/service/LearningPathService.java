package com.capacitix.service;

import com.capacitix.dto.LearningPathResponse;
import com.capacitix.entity.AssessmentAssignment;
import com.capacitix.entity.LearningPath;
import com.capacitix.entity.LearningPathItem;
import com.capacitix.entity.TrainingModule;
import com.capacitix.entity.User;

import java.util.List;
import java.util.Optional;

public interface LearningPathService {

    /**
     * Generates a personalized AI learning path.
     */
    LearningPathResponse generateLearningPath(
            User employee
    );


    /**
     * Finds the learning path generated for an assessment.
     */
    Optional<LearningPath> getLearningPathByAssessment(
            AssessmentAssignment assessmentAssignment
    );


    /**
     * Checks whether a learning path exists for an assessment.
     */
    boolean existsByAssessment(
            AssessmentAssignment assessmentAssignment
    );


    /**
     * Creates and saves a learning path.
     */
    LearningPath createLearningPath(
            User employee,
            String title,
            String description,
            String skillGaps
    );


    /**
     * Saves an existing learning path.
     */
    LearningPath save(
            LearningPath learningPath
    );


    /**
     * Finds a learning path by ID.
     */
    Optional<LearningPath> findById(
            Long id
    );


    /**
     * Gets all learning paths belonging to an employee.
     */
    List<LearningPath> getEmployeeLearningPaths(
            User employee
    );


    /**
     * Gets the latest learning path.
     */
    Optional<LearningPath> getLatestLearningPath(
            User employee
    );


    /**
     * Gets the latest active learning path.
     */
    Optional<LearningPath> getActiveLearningPath(
            User employee
    );


    /**
     * Adds a training module to a learning path.
     */
    LearningPathItem addItem(
            LearningPath learningPath,
            TrainingModule trainingModule,
            Integer sequenceOrder,
            String targetSkill,
            String recommendationReason,
            LearningPathItem.Priority priority
    );


    /**
     * Gets all items in sequence order.
     */
    List<LearningPathItem> getLearningPathItems(
            LearningPath learningPath
    );


    /**
     * Gets a specific item by sequence order.
     */
    Optional<LearningPathItem> getLearningPathItem(
            LearningPath learningPath,
            Integer sequenceOrder
    );


    /**
     * Updates overall learning-path progress.
     */
    void updateLearningPathProgress(
            LearningPath learningPath
    );


    /**
     * Updates an individual learning-path item's progress.
     */
    LearningPathItem updateItemProgress(
            LearningPathItem item,
            Integer progressPercentage
    );


    /**
     * Pauses a learning path.
     */
    LearningPath pauseLearningPath(
            LearningPath learningPath
    );


    /**
     * Resumes a learning path.
     */
    LearningPath resumeLearningPath(
            LearningPath learningPath
    );


    /**
     * Marks a learning path as completed.
     */
    LearningPath completeLearningPath(
            LearningPath learningPath
    );


    /**
     * Deletes a learning path.
     */
    void deleteLearningPath(
            Long id
    );
}