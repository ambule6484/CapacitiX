
package com.capacitix.service;

import com.capacitix.entity.LearningPathItem;
import com.capacitix.entity.TrainingModule;

import java.util.List;
import java.util.Optional;

public interface TrainingModuleService {

    /**
     * Creates and saves a normal training module.
     */
    TrainingModule createModule(
            String title,
            String description,
            String skillName,
            TrainingModule.DifficultyLevel difficultyLevel,
            TrainingModule.ModuleType moduleType,
            String content,
            String resourceUrl,
            Integer estimatedMinutes
    );

    /**
     * Creates a personalized training module
     * for a specific learning-path item.
     */
    TrainingModule createPersonalizedModule(
            LearningPathItem learningPathItem,
            String title,
            String description,
            String skillName,
            TrainingModule.DifficultyLevel difficultyLevel,
            TrainingModule.ModuleType moduleType,
            String content,
            String resourceUrl,
            Integer estimatedMinutes
    );

    /**
     * Gets active personalized modules belonging
     * to a learning-path item.
     */
    List<TrainingModule> getPersonalizedModules(
            LearningPathItem learningPathItem
    );

    /**
     * Checks whether a personalized module exists
     * for a learning-path item.
     */
    boolean hasPersonalizedModule(
            LearningPathItem learningPathItem
    );

    /**
     * Saves an existing training module.
     */
    TrainingModule save(
            TrainingModule trainingModule
    );

    /**
     * Finds a training module by ID.
     */
    Optional<TrainingModule> findById(
            Long id
    );

    /**
     * Gets all training modules.
     */
    List<TrainingModule> getAllModules();

    /**
     * Gets all active training modules.
     */
    List<TrainingModule> getActiveModules();

    /**
     * Finds a module by title.
     */
    Optional<TrainingModule> findByTitle(
            String title
    );

    /**
     * Gets active modules for a skill.
     */
    List<TrainingModule> getModulesBySkill(
            String skillName
    );

    /**
     * Gets active modules for a skill and difficulty.
     */
    List<TrainingModule> getModulesBySkillAndDifficulty(
            String skillName,
            TrainingModule.DifficultyLevel difficultyLevel
    );

    /**
     * Gets active modules by difficulty.
     */
    List<TrainingModule> getModulesByDifficulty(
            TrainingModule.DifficultyLevel difficultyLevel
    );

    /**
     * Gets active modules by module type.
     */
    List<TrainingModule> getModulesByType(
            TrainingModule.ModuleType moduleType
    );

    /**
     * Searches active modules by skill.
     */
    List<TrainingModule> searchBySkill(
            String skillName
    );

    /**
     * Activates a module.
     */
    TrainingModule activateModule(
            Long id
    );

    /**
     * Deactivates a module.
     */
    TrainingModule deactivateModule(
            Long id
    );

    /**
     * Updates an existing module.
     */
    TrainingModule updateModule(
            Long id,
            String title,
            String description,
            String skillName,
            TrainingModule.DifficultyLevel difficultyLevel,
            TrainingModule.ModuleType moduleType,
            String content,
            String resourceUrl,
            Integer estimatedMinutes
    );

    /**
     * Deletes a module.
     */
    void deleteModule(
            Long id
    );

    /**
     * Gets a required module or throws an exception.
     */
    TrainingModule getRequiredModule(
            Long id
    );
}