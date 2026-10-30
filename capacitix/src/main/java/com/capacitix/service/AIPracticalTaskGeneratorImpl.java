package com.capacitix.service;

import com.capacitix.entity.LearningPath;
import com.capacitix.entity.LearningPathItem;
import com.capacitix.entity.PracticalTask;
import com.capacitix.entity.TrainingModule;
import com.capacitix.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI Practical Task Generator
 *
 * Generates personalized practical tasks using the local Ollama model.
 *
 * Flow:
 *
 * Assessment
 *      ↓
 * Skill Analysis
 *      ↓
 * Learning Path
 *      ↓
 * Learning Path Item
 *      ↓
 * Training Module
 *      ↓
 * AI Practical Tasks
 *
 * IMPORTANT:
 *
 * This class only generates PracticalTask entities.
 *
 * PracticalTaskService is responsible for persistence.
 */
@Service
@Slf4j
public class AIPracticalTaskGeneratorImpl
        implements AIPracticalTaskGenerator {

    // =========================================================
    // OLLAMA CONFIGURATION
    // =========================================================

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model:llama3.2}")
    private String ollamaModel;

    @Value("${ollama.timeout:180000}")
    private int ollamaTimeout;

    private volatile RestClient restClient;

    private static final int DEFAULT_TASK_COUNT = 3;

    // =========================================================
    // GENERATE TASKS FOR COMPLETE LEARNING PATH
    // =========================================================

    @Override
    public List<PracticalTask> generateTasks(
            User employee,
            LearningPath learningPath
    ) {

        List<PracticalTask> tasks = new ArrayList<>();

        if (employee == null) {

            log.warn(
                    "Practical task generation skipped: employee is null."
            );

            return tasks;
        }

        if (learningPath == null) {

            log.warn(
                    "Practical task generation skipped for employee {}: learning path is null.",
                    employee.getId()
            );

            return tasks;
        }

        if (learningPath.getItems() == null
                || learningPath.getItems().isEmpty()) {

            log.warn(
                    "No learning-path items found for employee {}.",
                    employee.getId()
            );

            return tasks;
        }

        log.info(
                "Starting practical task generation for employee {} ({}) with {} learning-path items.",
                employee.getId(),
                safe(employee.getFullName()),
                learningPath.getItems().size()
        );

        for (LearningPathItem item : learningPath.getItems()) {

            if (item == null) {
                continue;
            }

            TrainingModule trainingModule =
                    item.getTrainingModule();

            try {

                List<PracticalTask> itemTasks =
                        generateTasksForItem(
                                employee,
                                item,
                                trainingModule,
                                DEFAULT_TASK_COUNT
                        );

                if (itemTasks != null
                        && !itemTasks.isEmpty()) {

                    tasks.addAll(itemTasks);

                    log.info(
                            "Generated {} practical tasks for employee {} and skill '{}'.",
                            itemTasks.size(),
                            employee.getId(),
                            safe(item.getTargetSkill())
                    );

                } else {

                    log.warn(
                            "No practical tasks generated for employee {} and skill '{}'.",
                            employee.getId(),
                            safe(item.getTargetSkill())
                    );
                }

            } catch (Exception exception) {

                /*
                 * One learning-path item should not prevent
                 * other valid items from being processed.
                 */
                log.error(
                        "Failed to generate practical tasks for employee {} and skill '{}'.",
                        employee.getId(),
                        safe(item.getTargetSkill()),
                        exception
                );
            }
        }

        log.info(
                "Practical task generation completed for employee {}. Total generated tasks: {}",
                employee.getId(),
                tasks.size()
        );

        return tasks;
    }

    // =========================================================
    // GENERATE TASKS FOR ONE ITEM
    // =========================================================

    @Override
    public List<PracticalTask> generateTasksForItem(
            User employee,
            LearningPathItem learningPathItem,
            TrainingModule trainingModule
    ) {

        return generateTasksForItem(
                employee,
                learningPathItem,
                trainingModule,
                DEFAULT_TASK_COUNT
        );
    }

    // =========================================================
    // GENERATE SPECIFIC NUMBER OF TASKS
    // =========================================================

    @Override
    public List<PracticalTask> generateTasksForItem(
            User employee,
            LearningPathItem learningPathItem,
            TrainingModule trainingModule,
            int numberOfTasks
    ) {

        List<PracticalTask> tasks = new ArrayList<>();

        if (employee == null) {

            log.warn(
                    "Cannot generate practical task: employee is null."
            );

            return tasks;
        }

        if (learningPathItem == null) {

            log.warn(
                    "Cannot generate practical task for employee {}: learning-path item is null.",
                    employee.getId()
            );

            return tasks;
        }

        if (numberOfTasks <= 0) {
            numberOfTasks = DEFAULT_TASK_COUNT;
        }

        // -----------------------------------------------------
        // TARGET SKILL
        // -----------------------------------------------------

        String skillName =
                safe(learningPathItem.getTargetSkill());

        if (skillName.isBlank()
                && trainingModule != null) {

            skillName =
                    safe(trainingModule.getSkillName());
        }

        if (skillName.isBlank()) {

            log.error(
                    "Cannot generate practical task for employee {}: target skill is empty.",
                    employee.getId()
            );

            return tasks;
        }

        log.info(
                "Generating {} AI practical tasks for employee {} | Skill: {}",
                numberOfTasks,
                employee.getId(),
                skillName
        );

        // -----------------------------------------------------
        // BUILD PROMPT
        // -----------------------------------------------------

        String prompt =
                buildPrompt(
                        employee,
                        learningPathItem,
                        trainingModule,
                        skillName,
                        numberOfTasks
                );

        // -----------------------------------------------------
        // CALL OLLAMA
        // -----------------------------------------------------

        String aiResponse =
                callOllama(
                        prompt,
                        employee,
                        skillName
                );

        if (aiResponse == null
                || aiResponse.isBlank()) {

            log.error(
                    "Ollama returned an empty response for employee {} and skill '{}'.",
                    employee.getId(),
                    skillName
            );

            return tasks;
        }

        log.info(
                "Ollama returned {} characters for employee {} and skill '{}'.",
                aiResponse.length(),
                employee.getId(),
                skillName
        );

        log.debug(
                "Ollama response for employee {}:\n{}",
                employee.getId(),
                aiResponse
        );

        // -----------------------------------------------------
        // PARSE AI RESPONSE
        // -----------------------------------------------------

        tasks =
                parseTasks(
                        aiResponse,
                        employee,
                        learningPathItem,
                        trainingModule,
                        skillName
                );

        log.info(
                "Parsed {} practical tasks from Ollama for employee {} and skill '{}'.",
                tasks.size(),
                employee.getId(),
                skillName
        );

        return tasks;
    }

    // =========================================================
    // BUILD AI PROMPT
    // =========================================================

    private String buildPrompt(
            User employee,
            LearningPathItem item,
            TrainingModule module,
            String skillName,
            int numberOfTasks
    ) {

        String employeeName =
                safe(employee.getFullName());

        String currentLevel =
                "UNKNOWN";

        String requiredLevel =
                "UNKNOWN";

        String priority =
                "MEDIUM";

        if (module != null
                && module.getDifficultyLevel() != null) {

            currentLevel =
                    module.getDifficultyLevel().name();
        }

        if (item.getPriority() != null) {

            priority =
                    item.getPriority().name();
        }

        String recommendationReason =
                safe(item.getRecommendationReason());

        String moduleTitle = "";
        String moduleDescription = "";
        String moduleContent = "";
        String technology = "";

        if (module != null) {

            moduleTitle =
                    safe(module.getTitle());

            moduleDescription =
                    safe(module.getDescription());

            moduleContent =
                    safe(module.getContent());

            /*
             * TrainingModule currently exposes skillName.
             * Use it as the primary technology/context.
             */
            technology =
                    safe(module.getSkillName());

            if (module.getDifficultyLevel() != null) {

                currentLevel =
                        module.getDifficultyLevel().name();
            }
        }

        /*
         * If the module does not provide a technology,
         * use the target skill as a safe fallback.
         */
        if (technology.isBlank()) {
            technology = skillName;
        }

        StringBuilder prompt =
                new StringBuilder();

        prompt.append("""
                You are an enterprise employee training
                practical-task generator for a platform
                called CapacitiX.

                Your job is to create realistic hands-on
                practical tasks for one employee.

                The tasks MUST be personalized to the
                employee's identified skill gap and
                personalized learning path.

                IMPORTANT RULES:

                1. Generate exactly the requested number of tasks.

                2. Every task MUST directly improve the target skill.

                3. Do NOT generate generic tasks unrelated
                   to the target skill.

                4. Do NOT generate theoretical questions.

                5. Every task must require the employee to
                   actually build, implement, configure,
                   debug, analyze, test, or demonstrate something.

                6. Tasks must be measurable.

                7. Tasks must have a clear expected outcome.

                8. Tasks must be realistic for workplace learning.

                9. Difficulty must match the employee's
                   current learning level.

                10. Higher-priority skill gaps should receive
                    meaningful practical work.

                11. Do not introduce unrelated technologies.

                12. Avoid duplicate tasks.

                13. Use the training module context when available.

                14. Every task must help close the identified skill gap.

                15. Do not use placeholder text.

                16. Do not mention that you are an AI.

                17. Do not return Markdown tables.

                18. Do not return explanations outside the task records.

                19. Return ONLY the task records.

                20. Each task MUST start with TASK and end with END_TASK.

                EXACT OUTPUT FORMAT:

                TASK
                Title: ...
                Description: ...
                ExpectedOutcome: ...
                Requirements: ...
                Technology: ...
                Difficulty: BEGINNER
                EstimatedMinutes: 30
                END_TASK

                TASK
                Title: ...
                Description: ...
                ExpectedOutcome: ...
                Requirements: ...
                Technology: ...
                Difficulty: INTERMEDIATE
                EstimatedMinutes: 45
                END_TASK

                """);

        prompt.append("\nEMPLOYEE INFORMATION\n");

        prompt.append("Employee Name: ")
                .append(employeeName)
                .append("\n");

        prompt.append("Target Skill: ")
                .append(skillName)
                .append("\n");

        prompt.append("Priority: ")
                .append(priority)
                .append("\n");

        prompt.append("Current Learning Level: ")
                .append(currentLevel)
                .append("\n");

        prompt.append("Required Skill Level: ")
                .append(requiredLevel)
                .append("\n");

        prompt.append(
                "Learning Path Recommendation: "
        )
                .append(recommendationReason)
                .append("\n");

        if (!moduleTitle.isBlank()) {

            prompt.append("Training Module: ")
                    .append(moduleTitle)
                    .append("\n");
        }

        if (!moduleDescription.isBlank()) {

            prompt.append("Module Description: ")
                    .append(moduleDescription)
                    .append("\n");
        }

        if (!moduleContent.isBlank()) {

            String shortenedContent =
                    moduleContent.length() > 4000
                            ? moduleContent.substring(0, 4000)
                            : moduleContent;

            prompt.append("Learning Content:\n")
                    .append(shortenedContent)
                    .append("\n");
        }

        prompt.append("Primary Technology: ")
                .append(technology)
                .append("\n");

        prompt.append("\n");

        prompt.append(
                "Generate exactly "
        )
        .append(numberOfTasks)
        .append(
                " unique practical tasks for the target skill."
        );

        return prompt.toString();
    }

    // =========================================================
    // CALL OLLAMA
    // =========================================================

    private String callOllama(
            String prompt,
            User employee,
            String skillName
    ) {

        try {

            RestClient client =
                    getRestClient();

            OllamaGenerateRequest request =
                    new OllamaGenerateRequest(
                            ollamaModel,
                            prompt,
                            false
                    );

            log.info(
                    "Calling Ollama | URL: {} | Model: {} | Employee: {} | Skill: {} | Timeout: {} ms",
                    ollamaBaseUrl,
                    ollamaModel,
                    employee.getId(),
                    skillName,
                    ollamaTimeout
            );

            OllamaGenerateResponse response =
                    client.post()
                            .uri("/api/generate")
                            .contentType(
                                    MediaType.APPLICATION_JSON
                            )
                            .body(request)
                            .retrieve()
                            .body(
                                    OllamaGenerateResponse.class
                            );

            if (response == null) {

                log.error(
                        "Ollama returned NULL response for employee {} and skill '{}'.",
                        employee.getId(),
                        skillName
                );

                return "";
            }

            String generatedText =
                    safe(response.response());

            if (generatedText.isBlank()) {

                log.error(
                        "Ollama returned an empty response for employee {} and skill '{}'.",
                        employee.getId(),
                        skillName
                );

                return "";
            }

            return generatedText;

        } catch (Exception exception) {

            /*
             * IMPORTANT:
             *
             * Previously this exception was silently ignored.
             *
             * That made the practical_tasks table remain empty
             * without showing the real reason.
             */

            log.error(
                    "OLLAMA PRACTICAL TASK GENERATION FAILED | Employee: {} | Skill: {} | Model: {} | URL: {} | Error: {}",
                    employee != null
                            ? employee.getId()
                            : null,
                    skillName,
                    ollamaModel,
                    ollamaBaseUrl,
                    exception.getMessage(),
                    exception
            );

            return "";
        }
    }

    // =========================================================
    // REST CLIENT
    // =========================================================

    private RestClient getRestClient() {

        RestClient currentClient =
                restClient;

        if (currentClient != null) {
            return currentClient;
        }

        synchronized (this) {

            if (restClient == null) {

                SimpleClientHttpRequestFactory factory =
                        new SimpleClientHttpRequestFactory();

                int timeout =
                        Math.max(
                                ollamaTimeout,
                                10000
                        );

                factory.setConnectTimeout(timeout);
                factory.setReadTimeout(timeout);

                restClient =
                        RestClient.builder()
                                .baseUrl(
                                        removeTrailingSlash(
                                                ollamaBaseUrl
                                        )
                                )
                                .requestFactory(factory)
                                .build();
            }

            return restClient;
        }
    }

    // =========================================================
    // PARSE AI RESPONSE
    // =========================================================

    private List<PracticalTask> parseTasks(
            String aiResponse,
            User employee,
            LearningPathItem learningPathItem,
            TrainingModule trainingModule,
            String skillName
    ) {

        List<PracticalTask> tasks =
                new ArrayList<>();

        if (aiResponse == null
                || aiResponse.isBlank()) {

            return tasks;
        }

        String normalized =
                aiResponse
                        .replace("\r", "")
                        .replace("```text", "")
                        .replace("```", "")
                        .trim();

        /*
         * Match:
         *
         * TASK
         * ...
         * END_TASK
         */
        Pattern taskPattern =
                Pattern.compile(
                        "(?is)\\bTASK\\s*(.*?)\\s*END_TASK\\b"
                );

        Matcher matcher =
                taskPattern.matcher(normalized);

        int taskOrder = 1;

        while (matcher.find()) {

            String block =
                    matcher.group(1).trim();

            if (block.isBlank()) {
                continue;
            }

            String title =
                    extractField(
                            block,
                            "Title"
                    );

            String description =
                    extractField(
                            block,
                            "Description"
                    );

            String expectedOutcome =
                    extractField(
                            block,
                            "ExpectedOutcome"
                    );

            String requirements =
                    extractField(
                            block,
                            "Requirements"
                    );

            String technology =
                    extractField(
                            block,
                            "Technology"
                    );

            String difficulty =
                    extractField(
                            block,
                            "Difficulty"
                    );

            String estimatedMinutes =
                    extractField(
                            block,
                            "EstimatedMinutes"
                    );

            /*
             * Title and description are mandatory.
             */
            if (title.isBlank()
                    || description.isBlank()) {

                log.warn(
                        "Skipping AI practical task because Title or Description is missing."
                );

                continue;
            }

            /*
             * Technology fallback.
             */
            if (technology.isBlank()
                    && trainingModule != null) {

                technology =
                        safe(
                                trainingModule.getSkillName()
                        );
            }

            if (technology.isBlank()) {
                technology = skillName;
            }

            PracticalTask task =
                    PracticalTask.builder()

                            // Employee
                            .employee(employee)

                            // Learning path
                            .learningPathItem(
                                    learningPathItem
                            )

                            // Training module
                            .trainingModule(
                                    trainingModule
                            )

                            // Basic task information
                            .title(
                                    limit(
                                            title,
                                            250
                                    )
                            )

                            .skillName(
                                    limit(
                                            skillName,
                                            150
                                    )
                            )

                            .description(
                                    description.trim()
                            )

                            .expectedOutcome(
                                    emptyToNull(
                                            expectedOutcome
                                    )
                            )

                            .requirements(
                                    emptyToNull(
                                            requirements
                                    )
                            )

                            .technology(
                                    emptyToNull(
                                            limit(
                                                    technology,
                                                    150
                                            )
                                    )
                            )

                            // Difficulty
                            .difficulty(
                                    parseDifficulty(
                                            difficulty
                                    )
                            )

                            // Estimated time
                            .estimatedMinutes(
                                    parseMinutes(
                                            estimatedMinutes
                                    )
                            )

                            // Skill information
                            .skillScore(null)
                            .requiredSkillLevel(null)
                            .currentSkillLevel(null)
                            .gapPercentage(null)

                            // Priority
                            .priority(
                                    convertPriority(
                                            learningPathItem
                                                    .getPriority()
                                    )
                            )

                            // Initial state
                            .status(
                                    PracticalTask.TaskStatus.PENDING
                            )

                            .progressPercentage(0)

                            // AI generated
                            .aiGenerated(true)

                            // Order
                            .taskOrder(
                                    taskOrder++
                            )

                            .build();

            tasks.add(task);
        }

        /*
         * Diagnostic logging.
         */
        if (tasks.isEmpty()) {

            log.error(
                    "Ollama response was received but NO practical tasks could be parsed."
            );

            log.error(
                    "AI response:\n{}",
                    aiResponse
            );
        }

        return tasks;
    }

    // =========================================================
    // EXTRACT FIELD
    // =========================================================

    private String extractField(
            String block,
            String fieldName
    ) {

        /*
         * Fields are expected to look like:
         *
         * Title: Build...
         *
         * Description: Build...
         *
         * ExpectedOutcome: ...
         *
         * The look-ahead stops at the next known field.
         */
        Pattern pattern =
                Pattern.compile(
                        "(?ims)^\\s*"
                                + Pattern.quote(fieldName)
                                + "\\s*:\\s*"
                                + "(.*?)"
                                + "(?=^\\s*(?:Title|Description|ExpectedOutcome|Requirements|Technology|Difficulty|EstimatedMinutes)\\s*:|\\z)"
                );

        Matcher matcher =
                pattern.matcher(block);

        if (matcher.find()) {

            return matcher.group(1)
                    .trim();
        }

        return "";
    }

    // =========================================================
    // PARSE DIFFICULTY
    // =========================================================

    private PracticalTask.DifficultyLevel parseDifficulty(
            String value
    ) {

        if (value == null
                || value.isBlank()) {

            return PracticalTask.DifficultyLevel.BEGINNER;
        }

        String normalized =
                value.trim()
                        .toUpperCase(Locale.ROOT);

        if (normalized.contains("ADVANCED")) {

            return PracticalTask.DifficultyLevel.ADVANCED;
        }

        if (normalized.contains("INTERMEDIATE")) {

            return PracticalTask.DifficultyLevel.INTERMEDIATE;
        }

        return PracticalTask.DifficultyLevel.BEGINNER;
    }

    // =========================================================
    // CONVERT PRIORITY
    // =========================================================

    private PracticalTask.Priority convertPriority(
            LearningPathItem.Priority priority
    ) {

        if (priority == null) {

            return PracticalTask.Priority.MEDIUM;
        }

        return switch (priority) {

            case HIGH ->
                    PracticalTask.Priority.HIGH;

            case LOW ->
                    PracticalTask.Priority.LOW;

            default ->
                    PracticalTask.Priority.MEDIUM;
        };
    }

    // =========================================================
    // PARSE ESTIMATED MINUTES
    // =========================================================

    private Integer parseMinutes(
            String value
    ) {

        if (value == null
                || value.isBlank()) {

            return 30;
        }

        Matcher matcher =
                Pattern.compile(
                        "\\d+"
                )
                .matcher(value);

        if (!matcher.find()) {

            return 30;
        }

        try {

            int minutes =
                    Integer.parseInt(
                            matcher.group()
                    );

            if (minutes <= 0) {
                return 30;
            }

            return Math.min(
                    minutes,
                    480
            );

        } catch (NumberFormatException exception) {

            return 30;
        }
    }

    // =========================================================
    // SAFE STRING
    // =========================================================

    private String safe(
            String value
    ) {

        return value == null
                ? ""
                : value.trim();
    }

    // =========================================================
    // EMPTY TO NULL
    // =========================================================

    private String emptyToNull(
            String value
    ) {

        if (value == null
                || value.isBlank()) {

            return null;
        }

        return value.trim();
    }

    // =========================================================
    // LIMIT STRING LENGTH
    // =========================================================

    private String limit(
            String value,
            int maxLength
    ) {

        if (value == null) {
            return "";
        }

        String trimmed =
                value.trim();

        if (trimmed.length() <= maxLength) {

            return trimmed;
        }

        return trimmed
                .substring(
                        0,
                        maxLength
                )
                .trim();
    }

    // =========================================================
    // REMOVE TRAILING SLASH
    // =========================================================

    private String removeTrailingSlash(
            String value
    ) {

        if (value == null
                || value.isBlank()) {

            return "http://localhost:11434";
        }

        String result =
                value.trim();

        while (result.endsWith("/")) {

            result =
                    result.substring(
                            0,
                            result.length() - 1
                    );
        }

        return result;
    }

    // =========================================================
    // OLLAMA REQUEST
    // =========================================================

    private record OllamaGenerateRequest(
            String model,
            String prompt,
            boolean stream
    ) {
    }

    // =========================================================
    // OLLAMA RESPONSE
    // =========================================================

    private record OllamaGenerateResponse(
            String response
    ) {
    }
}