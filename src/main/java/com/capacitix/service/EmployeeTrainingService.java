
package com.capacitix.service;

import com.capacitix.entity.EmployeeTrainingProgress;
import com.capacitix.entity.LearningPath;
import com.capacitix.entity.LearningPathItem;
import com.capacitix.entity.User;

import java.util.List;
import java.util.Optional;

public interface EmployeeTrainingService {

    // =========================================================
    // SAVED LEARNING PATH
    // =========================================================

    /**
     * Gets the active saved learning path for the employee.
     *
     * IMPORTANT:
     * This method only loads the learning path from the database.
     * It does NOT generate or recalculate an AI learning path.
     *
     * @param employee logged-in employee
     * @return active saved learning path if available
     */
    Optional<LearningPath> getActiveLearningPath(
            User employee
    );

    /**
     * Gets the latest saved learning path for the employee.
     *
     * No AI generation is performed.
     *
     * @param employee logged-in employee
     * @return latest saved learning path if available
     */
    Optional<LearningPath> getLatestLearningPath(
            User employee
    );


    // =========================================================
    // LEARNING PATH TRAINING ITEMS
    // =========================================================

    /**
     * Gets the training items from the employee's saved
     * learning path in sequence order.
     *
     * The employee training page must use these items
     * instead of creating hardcoded training modules.
     *
     * @param employee logged-in employee
     * @return saved learning-path training items
     */
    List<LearningPathItem> getEmployeeTrainingItems(
            User employee
    );


    // =========================================================
    // EMPLOYEE TRAINING PROGRESS
    // =========================================================

    /**
     * Gets all training progress records for the employee.
     *
     * @param employee logged-in employee
     * @return employee progress records
     */
    List<EmployeeTrainingProgress> getEmployeeProgress(
            User employee
    );

    /**
     * Gets progress for a particular learning-path item.
     *
     * @param employee logged-in employee
     * @param learningPathItem saved learning-path item
     * @return progress record if available
     */
    Optional<EmployeeTrainingProgress> getProgress(
            User employee,
            LearningPathItem learningPathItem
    );

    /**
     * Starts training for a saved learning-path item.
     *
     * @param employee logged-in employee
     * @param learningPathItem assigned learning-path item
     * @return updated progress
     */
    EmployeeTrainingProgress startTraining(
            User employee,
            LearningPathItem learningPathItem
    );

    /**
     * Updates progress for a saved learning-path item.
     *
     * @param employee logged-in employee
     * @param learningPathItem assigned learning-path item
     * @param progressPercentage progress from 0 to 100
     * @return updated progress
     */
    EmployeeTrainingProgress updateProgress(
            User employee,
            LearningPathItem learningPathItem,
            Integer progressPercentage
    );

    /**
     * Adds time spent on a saved learning-path item.
     *
     * @param employee logged-in employee
     * @param learningPathItem assigned learning-path item
     * @param minutes additional time spent
     * @return updated progress
     */
    EmployeeTrainingProgress addTimeSpent(
            User employee,
            LearningPathItem learningPathItem,
            Integer minutes
    );

    /**
     * Marks a saved learning-path item as completed.
     *
     * @param employee logged-in employee
     * @param learningPathItem assigned learning-path item
     * @return completed progress
     */
    EmployeeTrainingProgress completeTraining(
            User employee,
            LearningPathItem learningPathItem
    );

    /**
     * Saves an assessment score for a training item.
     *
     * @param employee logged-in employee
     * @param learningPathItem saved learning-path item
     * @param score assessment score
     * @return updated progress
     */
    EmployeeTrainingProgress updateAssessmentScore(
            User employee,
            LearningPathItem learningPathItem,
            Double score
    );

    /**
     * Saves employee feedback for a training item.
     *
     * @param employee logged-in employee
     * @param learningPathItem saved learning-path item
     * @param feedback employee feedback
     * @return updated progress
     */
    EmployeeTrainingProgress updateFeedback(
            User employee,
            LearningPathItem learningPathItem,
            String feedback
    );


    // =========================================================
    // TRAINING STATISTICS
    // =========================================================

    /**
     * Calculates overall progress using the employee's
     * saved learning-path items.
     *
     * @param employee logged-in employee
     * @return overall progress percentage
     */
    int getOverallProgress(
            User employee
    );

    /**
     * Counts completed items in the employee's training path.
     *
     * @param employee logged-in employee
     * @return completed item count
     */
    int getCompletedModuleCount(
            User employee
    );

    /**
     * Counts total items in the employee's saved learning path.
     *
     * @param employee logged-in employee
     * @return total training item count
     */
    int getTotalModuleCount(
            User employee
    );


    // =========================================================
    // PROGRESS LOOKUP
    // =========================================================

    /**
     * Gets a progress record by ID.
     *
     * @param id progress record ID
     * @return progress record if available
     */
    Optional<EmployeeTrainingProgress> findProgressById(
            Long id
    );
}