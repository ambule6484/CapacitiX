package com.capacitix.service;

import com.capacitix.entity.LearningPath;
import com.capacitix.entity.LearningPathItem;
import com.capacitix.entity.PracticalTask;
import com.capacitix.entity.TrainingModule;
import com.capacitix.entity.User;

import java.util.List;

/**
 * AI Practical Task Generator
 *
 * Responsible for generating personalized, realistic and measurable
 * practical tasks for employees based on their identified skill gaps.
 *
 * Main flow:
 *
 * Assessment
 *      ↓
 * Skill Analysis
 *      ↓
 * Skill Gap
 *      ↓
 * Learning Path
 *      ↓
 * Learning Path Item
 *      ↓
 * Training Module
 *      ↓
 * AI Practical Task
 *
 * IMPORTANT:
 *
 * This interface only defines the generation contract.
 * The implementation is responsible for:
 *
 * - Calling the configured AI model
 * - Generating tasks from the employee's actual skill gap
 * - Parsing the AI response
 * - Creating PracticalTask entities
 *
 * Persistence is handled by PracticalTaskService.
 */
public interface AIPracticalTaskGenerator {

    /**
     * Default number of practical tasks generated for
     * each learning-path item.
     */
    int DEFAULT_TASK_COUNT = 3;

    /**
     * Generate practical tasks for the employee's complete
     * personalized learning path.
     *
     * Existing tasks should be handled by the service layer
     * so that duplicate tasks are not created.
     *
     * @param employee employee who needs practical tasks
     * @param learningPath employee's personalized learning path
     * @return generated practical tasks
     */
    List<PracticalTask> generateTasks(
            User employee,
            LearningPath learningPath
    );

    /**
     * Generate practical tasks for a specific learning-path item.
     *
     * The generated tasks must be based on:
     *
     * - Target skill
     * - Employee skill gap
     * - Learning-path recommendation
     * - Training module
     * - Current competency level
     * - Required competency level
     *
     * @param employee employee who needs the tasks
     * @param learningPathItem learning-path item containing
     *                         the identified skill gap
     * @param trainingModule training module related to the skill
     * @return generated practical tasks
     */
    List<PracticalTask> generateTasksForItem(
            User employee,
            LearningPathItem learningPathItem,
            TrainingModule trainingModule
    );

    /**
     * Generate a specific number of practical tasks for
     * one learning-path item.
     *
     * The implementation should attempt to generate exactly
     * the requested number of tasks.
     *
     * @param employee employee who needs the tasks
     * @param learningPathItem learning-path item containing
     *                         the target skill
     * @param trainingModule training module related to the skill
     * @param numberOfTasks requested number of practical tasks
     * @return generated practical tasks
     */
    List<PracticalTask> generateTasksForItem(
            User employee,
            LearningPathItem learningPathItem,
            TrainingModule trainingModule,
            int numberOfTasks
    );
}