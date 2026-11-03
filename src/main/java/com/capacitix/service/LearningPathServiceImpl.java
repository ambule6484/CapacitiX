
package com.capacitix.service;

import com.capacitix.dto.LearningPathResponse;
import com.capacitix.entity.AssessmentAssignment;
import com.capacitix.entity.EmployeeTrainingProgress;
import com.capacitix.entity.LearningPath;
import com.capacitix.entity.LearningPathItem;
import com.capacitix.entity.TrainingModule;
import com.capacitix.entity.User;
import com.capacitix.repository.AssessmentAssignmentRepository;
import com.capacitix.repository.EmployeeTrainingProgressRepository;
import com.capacitix.repository.LearningPathItemRepository;
import com.capacitix.repository.LearningPathRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class LearningPathServiceImpl
        implements LearningPathService {

    private final SkillAnalysisService skillAnalysisService;

    private final AssessmentAssignmentRepository assessmentAssignmentRepository;

    private final AIService aiService;

    private final LearningPathRepository learningPathRepository;

    private final LearningPathItemRepository learningPathItemRepository;

    private final EmployeeTrainingProgressRepository
            employeeTrainingProgressRepository;

    private final TrainingModuleService trainingModuleService;


    // =========================================================
    // GENERATE / LOAD LEARNING PATH
    // =========================================================

    @Override
    public LearningPathResponse generateLearningPath(
            User employee
    ) {

        if (employee == null) {

            throw new IllegalArgumentException(
                    "Employee cannot be null"
            );
        }


        // =====================================================
        // FIND COMPLETED ASSESSMENTS
        // =====================================================

        List<AssessmentAssignment> completedAssignments =
                assessmentAssignmentRepository
                        .findByEmployeeAndStatusOrderByAssignedAtDesc(
                                employee,
                                "COMPLETED"
                        );


        // =====================================================
        // NO COMPLETED ASSESSMENT
        // =====================================================

        if (completedAssignments == null
                || completedAssignments.isEmpty()) {

            return LearningPathResponse.builder()
                    .employeeName(employee.getFullName())
                    .employeeRole(employee.getDesignation())
                    .totalSkillGaps(0)
                    .highPriorityGaps(0)
                    .mediumPriorityGaps(0)
                    .learningPath(new ArrayList<>())
                    .overallRecommendation(
                            "No completed assessment is available. "
                                    + "Complete an assessment first to "
                                    + "generate your personalized learning path."
                    )
                    .expectedOutcome(
                            "Your learning path will be generated "
                                    + "after assessment results are available."
                    )
                    .build();
        }


        // =====================================================
        // LATEST COMPLETED ASSESSMENT
        // =====================================================

        AssessmentAssignment assignment =
                completedAssignments.get(0);


        // =====================================================
        // CHECK EXISTING LEARNING PATH
        // =====================================================
        //
        // IMPORTANT:
        //
        // The database currently contains duplicate learning
        // paths for assessment assignment ID 5.
        //
        // Therefore we MUST NOT use:
        //
        // findByAssessmentAssignment(...)
        //
        // because that expects exactly one database row.
        //
        // Instead we load the latest learning path.
        // =====================================================

        Optional<LearningPath> existingLearningPath =
                learningPathRepository
                        .findTopByAssessmentAssignmentOrderByCreatedAtDesc(
                                assignment
                        );


        // =====================================================
        // EXISTING PATH
        // =====================================================

        if (existingLearningPath.isPresent()) {

            LearningPath storedPath =
                    existingLearningPath.get();


            List<LearningPathItem> storedItems =
                    learningPathItemRepository
                            .findByLearningPathOrderBySequenceOrderAsc(
                                    storedPath
                            );


            /*
             * Rebuild the DTO list from the persisted database
             * records instead of returning raw AI data.
             *
             * This keeps the Learning Path page and Training page
             * synchronized with the database.
             */

            List<com.capacitix.dto.LearningPathItem> dtoItems =
                    convertEntitiesToDTO(
                            storedItems
                    );


            return LearningPathResponse.builder()
                    .employeeName(employee.getFullName())
                    .employeeRole(employee.getDesignation())
                    .totalSkillGaps(storedItems.size())
                    .highPriorityGaps(
                            countPriority(
                                    storedItems,
                                    LearningPathItem.Priority.HIGH
                            )
                    )
                    .mediumPriorityGaps(
                            countPriority(
                                    storedItems,
                                    LearningPathItem.Priority.MEDIUM
                            )
                    )
                    .learningPath(dtoItems)
                    .overallRecommendation(
                            storedPath.getDescription() != null
                                    ? storedPath.getDescription()
                                    : "Your personalized learning path "
                                    + "has already been generated."
                    )
                    .expectedOutcome(
                            "Complete the personalized training modules "
                                    + "to improve the identified skill gaps."
                    )
                    .build();
        }


        // =====================================================
        // ANALYZE ASSESSMENT
        // =====================================================

        SkillAnalysisService.SkillAnalysisResult analysis =
                skillAnalysisService.analyzeAssignment(
                        assignment
                );


        // =====================================================
        // NO SKILL GAPS
        // =====================================================

        if (analysis.skillGaps() == null
                || analysis.skillGaps().isEmpty()) {

            return LearningPathResponse.builder()
                    .employeeName(employee.getFullName())
                    .employeeRole(employee.getDesignation())
                    .totalSkillGaps(0)
                    .highPriorityGaps(0)
                    .mediumPriorityGaps(0)
                    .learningPath(new ArrayList<>())
                    .overallRecommendation(
                            "No major skill gaps were identified "
                                    + "from your assessment."
                    )
                    .expectedOutcome(
                            "Continue practicing your current skills "
                                    + "and maintain your competency level."
                    )
                    .build();
        }


        // =====================================================
        // AI GENERATION
        // =====================================================

        LearningPathResponse response =
                aiService.generateLearningPath(
                        employee,
                        analysis
                );


        if (response == null) {

            throw new IllegalStateException(
                    "AI learning path generation returned no response."
            );
        }


        // =====================================================
        // SAVE MAIN LEARNING PATH
        // =====================================================

        LearningPath learningPath =
                LearningPath.builder()
                        .employee(employee)
                        .assessmentAssignment(assignment)
                        .title(
                                "Personalized Learning Path"
                        )
                        .description(
                                response.getOverallRecommendation()
                        )
                        .skillGaps(
                                analysis.skillGaps().toString()
                        )
                        .status(
                                LearningPath.LearningPathStatus.ACTIVE
                        )
                        .progressPercentage(0)
                        .build();


        learningPath =
                learningPathRepository.save(
                        learningPath
                );


        // =====================================================
        // SAVE AI ITEMS + TRAINING MODULES
        // =====================================================

        if (response.getLearningPath() != null
                && !response.getLearningPath().isEmpty()) {

            int fallbackSequence = 1;


            for (com.capacitix.dto.LearningPathItem dtoItem :
                    response.getLearningPath()) {

                if (dtoItem == null) {
                    continue;
                }


                // =================================================
                // SEQUENCE
                // =================================================

                Integer sequenceOrder =
                        dtoItem.getLearningOrder();


                if (sequenceOrder == null
                        || sequenceOrder < 1) {

                    sequenceOrder = fallbackSequence;
                }


                fallbackSequence++;


                // =================================================
                // CREATE TRAINING MODULE
                // =================================================

                TrainingModule trainingModule =
                        createTrainingModuleFromAIItem(
                                dtoItem
                        );


                // =================================================
                // PRIORITY
                // =================================================

                LearningPathItem.Priority priority =
                        convertPriority(
                                dtoItem.getPriority()
                        );


                // =================================================
                // SAVE LEARNING PATH ITEM
                // =================================================

                LearningPathItem savedItem =
                        addItem(
                                learningPath,
                                trainingModule,
                                sequenceOrder,
                                dtoItem.getSkillName(),
                                dtoItem.getGapReason(),
                                priority
                        );


                // =================================================
                // CONNECT MODULE TO ITEM
                // =================================================

                trainingModule.setLearningPathItem(
                        savedItem
                );

                trainingModule.setPersonalized(
                        true
                );


                trainingModuleService.save(
                        trainingModule
                );
            }
        }


        // =====================================================
        // REFRESH PATH PROGRESS
        // =====================================================

        updateLearningPathProgress(
                learningPath
        );


        // =====================================================
        // RETURN PERSISTED DATA
        // =====================================================

        List<LearningPathItem> savedItems =
                learningPathItemRepository
                        .findByLearningPathOrderBySequenceOrderAsc(
                                learningPath
                        );


        List<com.capacitix.dto.LearningPathItem> savedDTOItems =
                convertEntitiesToDTO(
                        savedItems
                );


        return LearningPathResponse.builder()
                .employeeName(employee.getFullName())
                .employeeRole(employee.getDesignation())
                .totalSkillGaps(savedItems.size())
                .highPriorityGaps(
                        countPriority(
                                savedItems,
                                LearningPathItem.Priority.HIGH
                        )
                )
                .mediumPriorityGaps(
                        countPriority(
                                savedItems,
                                LearningPathItem.Priority.MEDIUM
                        )
                )
                .learningPath(savedDTOItems)
                .overallRecommendation(
                        response.getOverallRecommendation()
                )
                .expectedOutcome(
                        response.getExpectedOutcome()
                )
                .build();
    }


    // =========================================================
    // CONVERT ENTITY → DTO
    // =========================================================

    private List<com.capacitix.dto.LearningPathItem>
    convertEntitiesToDTO(
            List<LearningPathItem> items
    ) {

        List<com.capacitix.dto.LearningPathItem> result =
                new ArrayList<>();


        if (items == null || items.isEmpty()) {

            return result;
        }


        for (LearningPathItem item : items) {

            if (item == null) {
                continue;
            }


            TrainingModule module =
                    item.getTrainingModule();


            if (module == null) {
                continue;
            }


            String duration =
                    module.getEstimatedMinutes() != null
                            ? module.getEstimatedMinutes()
                            + " minutes"
                            : null;


            com.capacitix.dto.LearningPathItem dto =
                    com.capacitix.dto.LearningPathItem.builder()
                            .skillName(
                                    item.getTargetSkill() != null
                                            ? item.getTargetSkill()
                                            : module.getSkillName()
                            )
                            .priority(
                                    item.getPriority() != null
                                            ? item.getPriority().name()
                                            : "MEDIUM"
                            )
                            .currentScore(null)
                            .gapReason(
                                    item.getRecommendationReason()
                            )
                            .topics(
                                    extractTopics(
                                            module.getContent()
                                    )
                            )
                            .practicalTask(
                                    "Complete the practical exercises "
                                            + "provided in this training module."
                            )
                            .estimatedDuration(
                                    duration
                            )
                            .expectedOutcome(
                                    "Improve practical understanding "
                                            + "and application of "
                                            + (
                                            module.getSkillName() != null
                                                    ? module.getSkillName()
                                                    : "this skill"
                                    )
                            )
                            .learningOrder(
                                    item.getSequenceOrder()
                            )
                            .build();


            result.add(dto);
        }


        return result;
    }


    // =========================================================
    // EXTRACT TOPICS FROM MODULE CONTENT
    // =========================================================

    private List<String> extractTopics(
            String content
    ) {

        List<String> topics =
                new ArrayList<>();


        if (content == null
                || content.isBlank()) {

            return topics;
        }


        String[] lines =
                content.split("\\R");


        boolean readingTopics = false;


        for (String rawLine : lines) {

            if (rawLine == null) {
                continue;
            }


            String line =
                    rawLine.trim();


            if (line.equalsIgnoreCase("TOPICS TO LEARN:")) {

                readingTopics = true;

                continue;
            }


            if (readingTopics) {

                if (line.isBlank()) {
                    continue;
                }


                if (line.endsWith(":")
                        && !line.matches("^\\d+\\.\\s+.*")) {

                    break;
                }


                String topic =
                        line.replaceFirst(
                                "^\\d+\\.\\s*",
                                ""
                        ).trim();


                if (!topic.isBlank()) {

                    topics.add(topic);
                }
            }
        }


        return topics;
    }


    // =========================================================
    // CREATE TRAINING MODULE
    // =========================================================

    private TrainingModule createTrainingModuleFromAIItem(
            com.capacitix.dto.LearningPathItem dtoItem
    ) {

        String skillName =
                safeValue(
                        dtoItem.getSkillName(),
                        "General Skill"
                );


        String title =
                skillName
                        + " - Personalized Training";


        String description =
                safeValue(
                        dtoItem.getGapReason(),
                        "Personalized training recommended "
                                + "based on the employee's skill gap."
                );


        String content =
                buildTrainingContent(
                        dtoItem
                );


        Integer estimatedMinutes =
                calculateEstimatedMinutes(
                        dtoItem.getEstimatedDuration()
                );


        TrainingModule.DifficultyLevel difficultyLevel =
                determineDifficulty(
                        dtoItem.getCurrentScore()
                );


        return trainingModuleService.createModule(
                title,
                description,
                skillName,
                difficultyLevel,
                TrainingModule.ModuleType.PRACTICE,
                content,
                null,
                estimatedMinutes
        );
    }


    // =========================================================
    // BUILD TRAINING CONTENT
    // =========================================================

    private String buildTrainingContent(
            com.capacitix.dto.LearningPathItem item
    ) {

        StringBuilder content =
                new StringBuilder();


        content.append(
                "PERSONALIZED TRAINING\n\n"
        );


        content.append(
                "SKILL:\n"
        );

        content.append(
                safeValue(
                        item.getSkillName(),
                        "Not specified"
                )
        );

        content.append("\n\n");


        content.append(
                "CURRENT ASSESSMENT SCORE:\n"
        );

        content.append(
                item.getCurrentScore() != null
                        ? item.getCurrentScore()
                        : "Not specified"
        );

        content.append("%\n\n");


        content.append(
                "WHY YOU NEED TO LEARN THIS:\n"
        );

        content.append(
                safeValue(
                        item.getGapReason(),
                        "This skill was identified "
                                + "as an area for improvement."
                )
        );

        content.append("\n\n");


        content.append(
                "TOPICS TO LEARN:\n"
        );


        if (item.getTopics() != null
                && !item.getTopics().isEmpty()) {

            int topicNumber = 1;


            for (String topic : item.getTopics()) {

                if (topic == null
                        || topic.isBlank()) {

                    continue;
                }


                content.append(
                        topicNumber++
                );

                content.append(". ");

                content.append(
                        topic.trim()
                );

                content.append("\n");
            }

        } else {

            content.append(
                    "Follow the recommended fundamentals "
                            + "and practical concepts for this skill.\n"
            );
        }


        content.append(
                "\nPRACTICAL TASK:\n"
        );

        content.append(
                safeValue(
                        item.getPracticalTask(),
                        "Complete practical exercises "
                                + "using this skill."
                )
        );

        content.append("\n\n");


        content.append(
                "ESTIMATED DURATION:\n"
        );

        content.append(
                safeValue(
                        item.getEstimatedDuration(),
                        "Not specified"
                )
        );

        content.append("\n\n");


        content.append(
                "EXPECTED OUTCOME:\n"
        );

        content.append(
                safeValue(
                        item.getExpectedOutcome(),
                        "Improve practical understanding "
                                + "and application of this skill."
                )
        );


        return content.toString();
    }


    // =========================================================
    // PRIORITY
    // =========================================================

    private LearningPathItem.Priority convertPriority(
            String priority
    ) {

        if (priority == null
                || priority.isBlank()) {

            return LearningPathItem.Priority.MEDIUM;
        }


        try {

            return LearningPathItem.Priority.valueOf(
                    priority.trim().toUpperCase()
            );

        } catch (IllegalArgumentException exception) {

            return LearningPathItem.Priority.MEDIUM;
        }
    }


    // =========================================================
    // COUNT PRIORITY
    // =========================================================

    private int countPriority(
            List<LearningPathItem> items,
            LearningPathItem.Priority priority
    ) {

        if (items == null
                || priority == null) {

            return 0;
        }


        int count = 0;


        for (LearningPathItem item : items) {

            if (item != null
                    && item.getPriority() == priority) {

                count++;
            }
        }


        return count;
    }


    // =========================================================
    // ESTIMATED TIME
    // =========================================================

    private Integer calculateEstimatedMinutes(
            String duration
    ) {

        if (duration == null
                || duration.isBlank()) {

            return 60;
        }


        String value =
                duration
                        .toLowerCase()
                        .trim();


        try {

            String number =
                    value.replaceAll(
                            "[^0-9]",
                            ""
                    );


            if (!number.isBlank()) {

                int parsed =
                        Integer.parseInt(number);


                if (value.contains("day")) {

                    return Math.max(
                            30,
                            parsed * 60
                    );
                }


                if (value.contains("hour")) {

                    return Math.max(
                            30,
                            parsed * 60
                    );
                }


                if (value.contains("minute")) {

                    return Math.max(
                            15,
                            parsed
                    );
                }
            }

        } catch (NumberFormatException ignored) {
            // Use default.
        }


        return 60;
    }


    // =========================================================
    // DIFFICULTY
    // =========================================================

    private TrainingModule.DifficultyLevel determineDifficulty(
            Double currentScore
    ) {

        if (currentScore == null) {

            return TrainingModule.DifficultyLevel.BEGINNER;
        }


        if (currentScore < 40) {

            return TrainingModule.DifficultyLevel.BEGINNER;
        }


        if (currentScore < 60) {

            return TrainingModule.DifficultyLevel.INTERMEDIATE;
        }


        return TrainingModule.DifficultyLevel.ADVANCED;
    }


    // =========================================================
    // SAFE VALUE
    // =========================================================

    private String safeValue(
            String value,
            String defaultValue
    ) {

        if (value == null
                || value.isBlank()) {

            return defaultValue;
        }


        return value.trim();
    }


    // =========================================================
    // ASSESSMENT LOOKUP
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public Optional<LearningPath> getLearningPathByAssessment(
            AssessmentAssignment assessmentAssignment
    ) {

        if (assessmentAssignment == null) {

            return Optional.empty();
        }


        /*
         * IMPORTANT:
         *
         * Use the latest learning path because duplicate rows
         * may already exist in older database data.
         */
        return learningPathRepository
                .findTopByAssessmentAssignmentOrderByCreatedAtDesc(
                        assessmentAssignment
                );
    }


    @Override
    @Transactional(readOnly = true)
    public boolean existsByAssessment(
            AssessmentAssignment assessmentAssignment
    ) {

        if (assessmentAssignment == null) {

            return false;
        }


        return learningPathRepository
                .existsByAssessmentAssignment(
                        assessmentAssignment
                );
    }


    // =========================================================
    // CREATE LEARNING PATH
    // =========================================================

    @Override
    public LearningPath createLearningPath(
            User employee,
            String title,
            String description,
            String skillGaps
    ) {

        if (employee == null) {

            throw new IllegalArgumentException(
                    "Employee cannot be null"
            );
        }


        if (title == null
                || title.isBlank()) {

            throw new IllegalArgumentException(
                    "Learning path title is required"
            );
        }


        LearningPath learningPath =
                LearningPath.builder()
                        .employee(employee)
                        .title(title.trim())
                        .description(description)
                        .skillGaps(skillGaps)
                        .status(
                                LearningPath.LearningPathStatus.ACTIVE
                        )
                        .progressPercentage(0)
                        .build();


        return learningPathRepository.save(
                learningPath
        );
    }


    // =========================================================
    // SAVE
    // =========================================================

    @Override
    public LearningPath save(
            LearningPath learningPath
    ) {

        if (learningPath == null) {

            throw new IllegalArgumentException(
                    "Learning path cannot be null"
            );
        }


        return learningPathRepository.save(
                learningPath
        );
    }


    // =========================================================
    // FIND BY ID
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public Optional<LearningPath> findById(
            Long id
    ) {

        if (id == null) {

            return Optional.empty();
        }


        return learningPathRepository.findById(id);
    }


    // =========================================================
    // EMPLOYEE PATHS
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<LearningPath> getEmployeeLearningPaths(
            User employee
    ) {

        if (employee == null) {

            return List.of();
        }


        return learningPathRepository.findByEmployee(
                employee
        );
    }


    @Override
    @Transactional(readOnly = true)
    public Optional<LearningPath> getLatestLearningPath(
            User employee
    ) {

        if (employee == null) {

            return Optional.empty();
        }


        return learningPathRepository
                .findTopByEmployeeOrderByCreatedAtDesc(
                        employee
                );
    }


    @Override
    @Transactional(readOnly = true)
    public Optional<LearningPath> getActiveLearningPath(
            User employee
    ) {

        if (employee == null) {

            return Optional.empty();
        }


        return learningPathRepository
                .findTopByEmployeeAndStatusOrderByCreatedAtDesc(
                        employee,
                        LearningPath.LearningPathStatus.ACTIVE
                );
    }


    // =========================================================
    // ADD ITEM
    // =========================================================

    @Override
    public LearningPathItem addItem(
            LearningPath learningPath,
            TrainingModule trainingModule,
            Integer sequenceOrder,
            String targetSkill,
            String recommendationReason,
            LearningPathItem.Priority priority
    ) {

        if (learningPath == null) {

            throw new IllegalArgumentException(
                    "Learning path cannot be null"
            );
        }


        if (trainingModule == null) {

            throw new IllegalArgumentException(
                    "Training module cannot be null"
            );
        }


        if (sequenceOrder == null
                || sequenceOrder < 1) {

            throw new IllegalArgumentException(
                    "Sequence order must be greater than zero"
            );
        }


        LearningPathItem item =
                LearningPathItem.builder()
                        .learningPath(learningPath)
                        .trainingModule(trainingModule)
                        .sequenceOrder(sequenceOrder)
                        .targetSkill(targetSkill)
                        .recommendationReason(
                                recommendationReason
                        )
                        .priority(
                                priority != null
                                        ? priority
                                        : LearningPathItem.Priority.MEDIUM
                        )
                        .status(
                                LearningPathItem.ItemStatus.NOT_STARTED
                        )
                        .progressPercentage(0)
                        .build();


        LearningPathItem savedItem =
                learningPathItemRepository.save(
                        item
                );


        // =====================================================
        // INITIAL EMPLOYEE PROGRESS
        // =====================================================

        User employee =
                learningPath.getEmployee();


        if (employee != null
                && !employeeTrainingProgressRepository
                        .existsByEmployeeAndLearningPathItem(
                                employee,
                                savedItem
                        )) {

            EmployeeTrainingProgress progress =
                    EmployeeTrainingProgress.builder()
                            .employee(employee)
                            .learningPathItem(savedItem)
                            .progressPercentage(0)
                            .status(
                                    EmployeeTrainingProgress
                                            .TrainingStatus
                                            .NOT_STARTED
                            )
                            .started(false)
                            .timeSpentMinutes(0)
                            .build();


            employeeTrainingProgressRepository.save(
                    progress
            );
        }


        updateLearningPathProgress(
                learningPath
        );


        return savedItem;
    }


    // =========================================================
    // GET ITEMS
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<LearningPathItem> getLearningPathItems(
            LearningPath learningPath
    ) {

        if (learningPath == null) {

            return List.of();
        }


        return learningPathItemRepository
                .findByLearningPathOrderBySequenceOrderAsc(
                        learningPath
                );
    }


    // =========================================================
    // GET ITEM BY SEQUENCE
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public Optional<LearningPathItem> getLearningPathItem(
            LearningPath learningPath,
            Integer sequenceOrder
    ) {

        if (learningPath == null
                || sequenceOrder == null) {

            return Optional.empty();
        }


        return learningPathItemRepository
                .findByLearningPathAndSequenceOrder(
                        learningPath,
                        sequenceOrder
                );
    }


    // =========================================================
    // UPDATE PATH PROGRESS
    // =========================================================

    @Override
    public void updateLearningPathProgress(
            LearningPath learningPath
    ) {

        if (learningPath == null) {

            return;
        }


        List<LearningPathItem> items =
                learningPathItemRepository
                        .findByLearningPathOrderBySequenceOrderAsc(
                                learningPath
                        );


        if (items.isEmpty()) {

            learningPath.setProgressPercentage(0);

            learningPathRepository.save(
                    learningPath
            );

            return;
        }


        int totalProgress = 0;


        for (LearningPathItem item : items) {

            Integer progress =
                    item.getProgressPercentage();


            if (progress == null) {

                progress = 0;
            }


            totalProgress +=
                    Math.max(
                            0,
                            Math.min(
                                    100,
                                    progress
                            )
                    );
        }


        int overallProgress =
                totalProgress / items.size();


        learningPath.setProgressPercentage(
                overallProgress
        );


        if (overallProgress >= 100) {

            learningPath.setProgressPercentage(100);

            learningPath.setStatus(
                    LearningPath.LearningPathStatus.COMPLETED
            );

        } else if (
                learningPath.getStatus()
                        == LearningPath.LearningPathStatus.COMPLETED
        ) {

            learningPath.setStatus(
                    LearningPath.LearningPathStatus.ACTIVE
            );
        }


        learningPathRepository.save(
                learningPath
        );
    }


    // =========================================================
    // UPDATE ITEM PROGRESS
    // =========================================================

    @Override
    public LearningPathItem updateItemProgress(
            LearningPathItem item,
            Integer progressPercentage
    ) {

        if (item == null) {

            throw new IllegalArgumentException(
                    "Learning path item cannot be null"
            );
        }


        int progress =
                progressPercentage == null
                        ? 0
                        : Math.max(
                                0,
                                Math.min(
                                        100,
                                        progressPercentage
                                )
                        );


        item.setProgressPercentage(
                progress
        );


        if (progress >= 100) {

            item.setProgressPercentage(100);

            item.setStatus(
                    LearningPathItem.ItemStatus.COMPLETED
            );

        } else if (progress > 0) {

            item.setStatus(
                    LearningPathItem.ItemStatus.IN_PROGRESS
            );

        } else {

            item.setStatus(
                    LearningPathItem.ItemStatus.NOT_STARTED
            );
        }


        LearningPathItem savedItem =
                learningPathItemRepository.save(
                        item
                );


        updateLearningPathProgress(
                item.getLearningPath()
        );


        return savedItem;
    }


    // =========================================================
    // PAUSE
    // =========================================================

    @Override
    public LearningPath pauseLearningPath(
            LearningPath learningPath
    ) {

        if (learningPath == null) {

            throw new IllegalArgumentException(
                    "Learning path cannot be null"
            );
        }


        learningPath.setStatus(
                LearningPath.LearningPathStatus.PAUSED
        );


        return learningPathRepository.save(
                learningPath
        );
    }


    // =========================================================
    // RESUME
    // =========================================================

    @Override
    public LearningPath resumeLearningPath(
            LearningPath learningPath
    ) {

        if (learningPath == null) {

            throw new IllegalArgumentException(
                    "Learning path cannot be null"
            );
        }


        if (learningPath.getProgressPercentage() >= 100) {

            learningPath.setProgressPercentage(100);

            learningPath.setStatus(
                    LearningPath.LearningPathStatus.COMPLETED
            );

        } else {

            learningPath.setStatus(
                    LearningPath.LearningPathStatus.ACTIVE
            );
        }


        return learningPathRepository.save(
                learningPath
        );
    }


    // =========================================================
    // COMPLETE
    // =========================================================

    @Override
    public LearningPath completeLearningPath(
            LearningPath learningPath
    ) {

        if (learningPath == null) {

            throw new IllegalArgumentException(
                    "Learning path cannot be null"
            );
        }


        List<LearningPathItem> items =
                learningPathItemRepository
                        .findByLearningPathOrderBySequenceOrderAsc(
                                learningPath
                        );


        for (LearningPathItem item : items) {

            item.setProgressPercentage(100);

            item.setStatus(
                    LearningPathItem.ItemStatus.COMPLETED
            );
        }


        if (!items.isEmpty()) {

            learningPathItemRepository.saveAll(
                    items
            );
        }


        learningPath.setProgressPercentage(100);

        learningPath.setStatus(
                LearningPath.LearningPathStatus.COMPLETED
        );


        return learningPathRepository.save(
                learningPath
        );
    }


    // =========================================================
    // DELETE
    // =========================================================

    @Override
    public void deleteLearningPath(
            Long id
    ) {

        if (id == null) {

            throw new IllegalArgumentException(
                    "Learning path ID is required"
            );
        }


        LearningPath learningPath =
                learningPathRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Learning path not found: "
                                                + id
                                )
                        );


        List<LearningPathItem> items =
                learningPathItemRepository
                        .findByLearningPathOrderBySequenceOrderAsc(
                                learningPath
                        );


        for (LearningPathItem item : items) {

            employeeTrainingProgressRepository
                    .deleteByLearningPathItem(item);
        }


        learningPathItemRepository
                .deleteByLearningPath(learningPath);


        learningPathRepository.delete(
                learningPath
        );
    }
}