
package com.capacitix.service;

import com.capacitix.entity.LearningPathItem;
import com.capacitix.entity.TrainingModule;
import com.capacitix.repository.TrainingModuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class TrainingModuleServiceImpl
        implements TrainingModuleService {

    private final TrainingModuleRepository trainingModuleRepository;


    // =========================================================
    // CREATE NORMAL MODULE
    // =========================================================

    @Override
    public TrainingModule createModule(
            String title,
            String description,
            String skillName,
            TrainingModule.DifficultyLevel difficultyLevel,
            TrainingModule.ModuleType moduleType,
            String content,
            String resourceUrl,
            Integer estimatedMinutes
    ) {

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException(
                    "Training module title is required."
            );
        }

        if (skillName == null || skillName.isBlank()) {
            throw new IllegalArgumentException(
                    "Training module skill name is required."
            );
        }

        Optional<TrainingModule> existing =
                trainingModuleRepository
                        .findByTitleIgnoreCase(
                                title.trim()
                        );

        if (existing.isPresent()) {
            return existing.get();
        }

        TrainingModule trainingModule =
                TrainingModule.builder()
                        .title(title.trim())
                        .description(description)
                        .skillName(skillName.trim())
                        .difficultyLevel(
                                difficultyLevel != null
                                        ? difficultyLevel
                                        : TrainingModule.DifficultyLevel.BEGINNER
                        )
                        .moduleType(
                                moduleType != null
                                        ? moduleType
                                        : TrainingModule.ModuleType.ARTICLE
                        )
                        .content(content)
                        .resourceUrl(resourceUrl)
                        .estimatedMinutes(
                                estimatedMinutes != null
                                        && estimatedMinutes > 0
                                        ? estimatedMinutes
                                        : 30
                        )
                        .active(true)
                        .personalized(false)
                        .build();

        return trainingModuleRepository.save(
                trainingModule
        );
    }


    // =========================================================
    // CREATE PERSONALIZED MODULE
    // =========================================================

    /**
     * Creates a module specifically for one
     * LearningPathItem.
     */
    public TrainingModule createPersonalizedModule(
            LearningPathItem learningPathItem,
            String title,
            String description,
            String skillName,
            TrainingModule.DifficultyLevel difficultyLevel,
            TrainingModule.ModuleType moduleType,
            String content,
            String resourceUrl,
            Integer estimatedMinutes
    ) {

        if (learningPathItem == null) {
            throw new IllegalArgumentException(
                    "Learning path item is required."
            );
        }

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException(
                    "Training module title is required."
            );
        }

        if (skillName == null || skillName.isBlank()) {
            throw new IllegalArgumentException(
                    "Training module skill name is required."
            );
        }


        // =====================================================
        // PREVENT DUPLICATE PERSONALIZED MODULE
        // =====================================================

        List<TrainingModule> existingModules =
                trainingModuleRepository
                        .findByLearningPathItemOrderByCreatedAtAsc(
                                learningPathItem
                        );

        if (!existingModules.isEmpty()) {
            return existingModules.get(0);
        }


        // =====================================================
        // CREATE MODULE
        // =====================================================

        TrainingModule trainingModule =
                TrainingModule.builder()
                        .title(title.trim())
                        .description(description)
                        .skillName(skillName.trim())
                        .learningPathItem(learningPathItem)
                        .difficultyLevel(
                                difficultyLevel != null
                                        ? difficultyLevel
                                        : TrainingModule.DifficultyLevel.BEGINNER
                        )
                        .moduleType(
                                moduleType != null
                                        ? moduleType
                                        : TrainingModule.ModuleType.ARTICLE
                        )
                        .content(content)
                        .resourceUrl(resourceUrl)
                        .estimatedMinutes(
                                estimatedMinutes != null
                                        && estimatedMinutes > 0
                                        ? estimatedMinutes
                                        : 30
                        )
                        .active(true)
                        .personalized(true)
                        .build();

        return trainingModuleRepository.save(
                trainingModule
        );
    }


    // =========================================================
    // GET PERSONALIZED MODULES
    // =========================================================

    @Transactional(readOnly = true)
    public List<TrainingModule> getPersonalizedModules(
            LearningPathItem learningPathItem
    ) {

        if (learningPathItem == null) {
            return List.of();
        }

        return trainingModuleRepository
                .findByLearningPathItemAndActiveTrueOrderByCreatedAtAsc(
                        learningPathItem
                );
    }


    // =========================================================
    // CHECK PERSONALIZED MODULE
    // =========================================================

    @Transactional(readOnly = true)
    public boolean hasPersonalizedModule(
            LearningPathItem learningPathItem
    ) {

        if (learningPathItem == null) {
            return false;
        }

        return trainingModuleRepository
                .existsByLearningPathItem(
                        learningPathItem
                );
    }


    // =========================================================
    // SAVE
    // =========================================================

    @Override
    public TrainingModule save(
            TrainingModule trainingModule
    ) {

        if (trainingModule == null) {
            throw new IllegalArgumentException(
                    "Training module cannot be null."
            );
        }

        return trainingModuleRepository.save(
                trainingModule
        );
    }


    // =========================================================
    // FIND BY ID
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public Optional<TrainingModule> findById(
            Long id
    ) {

        if (id == null) {
            return Optional.empty();
        }

        return trainingModuleRepository.findById(id);
    }


    // =========================================================
    // GET ALL
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<TrainingModule> getAllModules() {

        return trainingModuleRepository.findAll();
    }


    // =========================================================
    // GET ACTIVE
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<TrainingModule> getActiveModules() {

        return trainingModuleRepository
                .findByActiveTrue();
    }


    // =========================================================
    // FIND BY TITLE
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public Optional<TrainingModule> findByTitle(
            String title
    ) {

        if (title == null || title.isBlank()) {
            return Optional.empty();
        }

        return trainingModuleRepository
                .findByTitleIgnoreCase(
                        title.trim()
                );
    }


    // =========================================================
    // GET BY SKILL
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<TrainingModule> getModulesBySkill(
            String skillName
    ) {

        if (skillName == null || skillName.isBlank()) {
            return List.of();
        }

        return trainingModuleRepository
                .findBySkillNameIgnoreCaseAndActiveTrue(
                        skillName.trim()
                );
    }


    // =========================================================
    // GET BY SKILL + DIFFICULTY
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<TrainingModule>
    getModulesBySkillAndDifficulty(
            String skillName,
            TrainingModule.DifficultyLevel difficultyLevel
    ) {

        if (skillName == null
                || skillName.isBlank()
                || difficultyLevel == null) {

            return List.of();
        }

        return trainingModuleRepository
                .findBySkillNameIgnoreCaseAndDifficultyLevelAndActiveTrue(
                        skillName.trim(),
                        difficultyLevel
                );
    }


    // =========================================================
    // GET BY DIFFICULTY
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<TrainingModule> getModulesByDifficulty(
            TrainingModule.DifficultyLevel difficultyLevel
    ) {

        if (difficultyLevel == null) {
            return List.of();
        }

        return trainingModuleRepository
                .findByDifficultyLevelAndActiveTrue(
                        difficultyLevel
                );
    }


    // =========================================================
    // GET BY TYPE
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<TrainingModule> getModulesByType(
            TrainingModule.ModuleType moduleType
    ) {

        if (moduleType == null) {
            return List.of();
        }

        return trainingModuleRepository
                .findByModuleTypeAndActiveTrue(
                        moduleType
                );
    }


    // =========================================================
    // SEARCH BY SKILL
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<TrainingModule> searchBySkill(
            String skillName
    ) {

        if (skillName == null || skillName.isBlank()) {
            return List.of();
        }

        return trainingModuleRepository
                .findBySkillNameContainingIgnoreCaseAndActiveTrue(
                        skillName.trim()
                );
    }


    // =========================================================
    // ACTIVATE
    // =========================================================

    @Override
    public TrainingModule activateModule(
            Long id
    ) {

        TrainingModule module =
                getRequiredModule(id);

        module.setActive(true);

        return trainingModuleRepository.save(
                module
        );
    }


    // =========================================================
    // DEACTIVATE
    // =========================================================

    @Override
    public TrainingModule deactivateModule(
            Long id
    ) {

        TrainingModule module =
                getRequiredModule(id);

        module.setActive(false);

        return trainingModuleRepository.save(
                module
        );
    }


    // =========================================================
    // UPDATE
    // =========================================================

    @Override
    public TrainingModule updateModule(
            Long id,
            String title,
            String description,
            String skillName,
            TrainingModule.DifficultyLevel difficultyLevel,
            TrainingModule.ModuleType moduleType,
            String content,
            String resourceUrl,
            Integer estimatedMinutes
    ) {

        TrainingModule module =
                getRequiredModule(id);

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException(
                    "Training module title is required."
            );
        }

        if (skillName == null || skillName.isBlank()) {
            throw new IllegalArgumentException(
                    "Training module skill name is required."
            );
        }

        module.setTitle(title.trim());
        module.setDescription(description);
        module.setSkillName(skillName.trim());

        if (difficultyLevel != null) {
            module.setDifficultyLevel(
                    difficultyLevel
            );
        }

        if (moduleType != null) {
            module.setModuleType(
                    moduleType
            );
        }

        module.setContent(content);
        module.setResourceUrl(resourceUrl);

        if (estimatedMinutes != null
                && estimatedMinutes > 0) {

            module.setEstimatedMinutes(
                    estimatedMinutes
            );
        }

        return trainingModuleRepository.save(
                module
        );
    }


    // =========================================================
    // DELETE
    // =========================================================

    @Override
    public void deleteModule(
            Long id
    ) {

        TrainingModule module =
                getRequiredModule(id);

        trainingModuleRepository.delete(
                module
        );
    }


    // =========================================================
    // REQUIRED MODULE
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public TrainingModule getRequiredModule(
            Long id
    ) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "Training module ID is required."
            );
        }

        return trainingModuleRepository
                .findById(id)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Training module not found: "
                                        + id
                        )
                );
    }
}