
package com.capacitix.service;

import com.capacitix.entity.LearningPath;
import com.capacitix.entity.LearningPathItem;
import com.capacitix.entity.PracticalTask;
import com.capacitix.entity.TrainingModule;
import com.capacitix.entity.User;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AIPracticalTaskGeneratorImpl
        implements AIPracticalTaskGenerator {

    // =========================================================
    // GROQ CONFIGURATION
    // =========================================================

    @Value("${groq.api-key:}")
    private String groqApiKey;

    @Value("${groq.base-url:https://api.groq.com/openai/v1}")
    private String groqBaseUrl;

    @Value("${groq.model:openai/gpt-oss-120b}")
    private String groqModel;

    @Value("${groq.timeout:180000}")
    private int groqTimeout;

    @Value("${groq.temperature:0.2}")
    private double groqTemperature;

    @Value("${groq.max-tokens:4096}")
    private int groqMaxTokens;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile RestClient restClient;

    private static final int DEFAULT_TASK_COUNT = 3;

    // =========================================================
    // INITIALIZATION
    // =========================================================

    @PostConstruct
    public void initialize() {

        if (groqApiKey == null || groqApiKey.isBlank()) {
            log.warn("GROQ_API_KEY is not configured.");
        }

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(
                Duration.ofMillis(15000)
        );

        factory.setReadTimeout(
                Duration.ofMillis(
                        Math.max(groqTimeout, 10000)
                )
        );

        restClient = RestClient.builder()
                .baseUrl(removeTrailingSlash(groqBaseUrl))
                .requestFactory(factory)
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + groqApiKey
                )
                .defaultHeader(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .build();

        log.info("========================================");
        log.info("PRACTICAL TASK GENERATOR INITIALIZED");
        log.info("Provider: Groq Cloud API");
        log.info("Model: {}", groqModel);
        log.info("API URL: {}", sanitizeUrl(groqBaseUrl));
        log.info("Timeout: {} ms", groqTimeout);
        log.info("Strategy: SINGLE REQUEST PER LEARNING ITEM");
        log.info("========================================");
    }

    // =========================================================
    // GENERATE TASKS FOR COMPLETE LEARNING PATH
    // =========================================================

    @Override
    public List<PracticalTask> generateTasks(
            User employee,
            LearningPath learningPath) {

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

                if (itemTasks != null && !itemTasks.isEmpty()) {

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

                log.error(
                        "Failed to generate practical tasks for employee {} and skill '{}': {}",
                        employee.getId(),
                        safe(item.getTargetSkill()),
                        exception.getMessage(),
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
            TrainingModule trainingModule) {

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
            int numberOfTasks) {

        List<PracticalTask> tasks = new ArrayList<>();

        if (employee == null) {
            log.warn("Cannot generate practical task: employee is null.");
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

        String skillName =
                safe(learningPathItem.getTargetSkill());

        if (skillName.isBlank() && trainingModule != null) {
            skillName = safe(trainingModule.getSkillName());
        }

        if (skillName.isBlank()) {
            log.error(
                    "Cannot generate practical task for employee {}: target skill is empty.",
                    employee.getId()
            );
            return tasks;
        }

        if (groqApiKey == null || groqApiKey.isBlank()) {
            log.error("Cannot generate tasks: GROQ_API_KEY is missing.");
            return tasks;
        }

        log.info(
                "Generating {} Groq practical tasks for employee {} | Skill: {}",
                numberOfTasks,
                employee.getId(),
                skillName
        );

        String prompt = buildPrompt(
                employee,
                learningPathItem,
                trainingModule,
                skillName,
                numberOfTasks
        );

        String aiResponse = callGroq(
                prompt,
                employee,
                skillName,
                numberOfTasks
        );

        if (aiResponse == null || aiResponse.isBlank()) {
            log.error(
                    "Groq returned an empty response for employee {} and skill '{}'.",
                    employee.getId(),
                    skillName
            );
            return tasks;
        }

        log.info(
                "Groq returned {} characters for employee {} and skill '{}'.",
                aiResponse.length(),
                employee.getId(),
                skillName
        );

        log.debug(
                "Groq response for employee {}:\n{}",
                employee.getId(),
                aiResponse
        );

        tasks = parseTasks(
                aiResponse,
                employee,
                learningPathItem,
                trainingModule,
                skillName
        );

        log.info(
                "Parsed {} practical tasks from Groq for employee {} and skill '{}'.",
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
            int numberOfTasks) {

        String employeeName = safe(employee.getFullName());

        String currentLevel = "UNKNOWN";
        String requiredLevel = "UNKNOWN";
        String priority = "MEDIUM";

        if (module != null
                && module.getDifficultyLevel() != null) {

            currentLevel = module.getDifficultyLevel().name();
        }

        if (item.getPriority() != null) {
            priority = item.getPriority().name();
        }

        String recommendationReason =
                safe(item.getRecommendationReason());

        String moduleTitle = "";
        String moduleDescription = "";
        String moduleContent = "";
        String technology = "";

        if (module != null) {

            moduleTitle = safe(module.getTitle());
            moduleDescription = safe(module.getDescription());
            moduleContent = safe(module.getContent());
            technology = safe(module.getSkillName());

            if (module.getDifficultyLevel() != null) {
                currentLevel = module.getDifficultyLevel().name();
            }
        }

        if (technology.isBlank()) {
            technology = skillName;
        }

        StringBuilder prompt = new StringBuilder();

        prompt.append("""
                You are an enterprise employee training
                practical-task generator for a platform
                called CapacitiX.

                Create realistic hands-on practical tasks
                for one employee based on the employee's
                identified skill gap and personalized learning path.

                IMPORTANT RULES:

                1. Generate exactly the requested number of tasks.
                2. Every task MUST directly improve the target skill.
                3. Do NOT generate generic unrelated tasks.
                4. Do NOT generate theoretical questions.
                5. Every task must require actual practical work.
                6. Tasks must be measurable.
                7. Tasks must have a clear expected outcome.
                8. Tasks must be realistic for workplace learning.
                9. Difficulty must match the employee's level.
                10. Higher-priority skill gaps need meaningful work.
                11. Do not introduce unrelated technologies.
                12. Avoid duplicate tasks.
                13. Use the training module context when available.
                14. Every task must help close the identified skill gap.
                15. Do not use placeholder text.
                16. Do not mention that you are an AI.
                17. Do not return Markdown tables.
                18. Do not return explanations outside task records.
                19. Return ONLY task records.
                20. Each task MUST start with TASK and end with END_TASK.
                21. Keep each field concise and useful.

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

        prompt.append("Learning Path Recommendation: ")
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

        prompt.append("\nGenerate exactly ")
                .append(numberOfTasks)
                .append(" unique practical tasks for the target skill.");

        return prompt.toString();
    }

    // =========================================================
    // CALL GROQ CHAT COMPLETIONS API
    // =========================================================

    private String callGroq(
            String prompt,
            User employee,
            String skillName,
            int numberOfTasks) {

        try {

            RestClient client = getRestClient();

            int tokenLimit = Math.max(
                    512,
                    Math.min(
                            8192,
                            Math.min(
                                    Math.max(groqMaxTokens, 512),
                                    700 + numberOfTasks * 500
                            )
                    )
            );

            double temperature = Math.max(
                    0.0,
                    Math.min(0.5, groqTemperature)
            );

            Map<String, Object> request = Map.of(
                    "model", groqModel,
                    "messages", List.of(
                            Map.of(
                                    "role", "system",
                                    "content",
                                    """
                                    You are a professional enterprise practical-task generator.
                                    Follow the user's requested task format exactly.
                                    Return only the requested task records.
                                    Do not include introductory or concluding text.
                                    """
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", prompt
                            )
                    ),
                    "temperature", temperature,
                    "max_completion_tokens", tokenLimit
            );

            log.info(
                    "Calling Groq | Model: {} | Employee: {} | Skill: {} | Tasks: {} | MaxTokens: {} | Timeout: {} ms",
                    groqModel,
                    employee.getId(),
                    skillName,
                    numberOfTasks,
                    tokenLimit,
                    groqTimeout
            );

            long startTime = System.nanoTime();

            String rawResponse = client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);

            long duration =
                    (System.nanoTime() - startTime) / 1_000_000;

            if (rawResponse == null || rawResponse.isBlank()) {

                log.error("Groq returned an empty HTTP response.");

                return "";
            }

            JsonNode root =
                    objectMapper.readTree(rawResponse);

            if (root == null || !root.isObject()) {
                log.error("Groq returned an invalid response.");
                return "";
            }

            if (root.has("error")
                    && !root.path("error").isNull()) {

                String errorMessage =
                        root.path("error")
                                .path("message")
                                .asText("Unknown Groq API error");

                log.error("Groq API error: {}", errorMessage);

                return "";
            }

            JsonNode choices = root.path("choices");

            if (!choices.isArray() || choices.isEmpty()) {

                log.error(
                        "Groq returned no completion choices."
                );

                return "";
            }

            JsonNode choice = choices.get(0);

            JsonNode message = choice.path("message");

            String generatedText =
                    message.path("content").asText("");

            if (generatedText.isBlank()) {

                log.error(
                        "Groq returned empty content. Finish reason: {}",
                        choice.path("finish_reason").asText("unknown")
                );

                return "";
            }

            log.info(
                    "Groq response completed in {} ms. Finish reason: {}",
                    duration,
                    choice.path("finish_reason").asText("unknown")
            );

            return generatedText.trim();

        } catch (ResourceAccessException exception) {

            log.error(
                    "Groq request timed out or connection failed. Timeout: {} ms. Employee: {}. Skill: {}",
                    groqTimeout,
                    employee != null ? employee.getId() : null,
                    skillName,
                    exception
            );

            return "";

        } catch (HttpStatusCodeException exception) {

            log.error(
                    "Groq HTTP error. Status: {}. Response: {}",
                    exception.getStatusCode(),
                    preview(exception.getResponseBodyAsString()),
                    exception
            );

            return "";

        } catch (Exception exception) {

            log.error(
                    "Groq practical task generation failed. Employee: {}. Skill: {}. Error: {}",
                    employee != null ? employee.getId() : null,
                    skillName,
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

        RestClient currentClient = restClient;

        if (currentClient != null) {
            return currentClient;
        }

        synchronized (this) {

            if (restClient == null) {

                if (groqApiKey == null || groqApiKey.isBlank()) {
                    throw new IllegalStateException(
                            "GROQ_API_KEY is not configured."
                    );
                }

                SimpleClientHttpRequestFactory factory =
                        new SimpleClientHttpRequestFactory();

                factory.setConnectTimeout(
                        Duration.ofMillis(15000)
                );

                factory.setReadTimeout(
                        Duration.ofMillis(
                                Math.max(groqTimeout, 10000)
                        )
                );

                restClient = RestClient.builder()
                        .baseUrl(removeTrailingSlash(groqBaseUrl))
                        .requestFactory(factory)
                        .defaultHeader(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + groqApiKey
                        )
                        .defaultHeader(
                                HttpHeaders.CONTENT_TYPE,
                                MediaType.APPLICATION_JSON_VALUE
                        )
                        .defaultHeader(
                                HttpHeaders.ACCEPT,
                                MediaType.APPLICATION_JSON_VALUE
                        )
                        .build();
            }
        }

        return restClient;
    }

    // =========================================================
    // PARSE AI RESPONSE
    // =========================================================

    private List<PracticalTask> parseTasks(
            String aiResponse,
            User employee,
            LearningPathItem learningPathItem,
            TrainingModule trainingModule,
            String skillName) {

        List<PracticalTask> tasks = new ArrayList<>();

        if (aiResponse == null || aiResponse.isBlank()) {
            return tasks;
        }

        String normalized = aiResponse
                .replace("\r", "")
                .replace("```text", "")
                .replace("```", "")
                .trim();

        Pattern taskPattern =
                Pattern.compile("(?is)\\bTASK\\s*(.*?)\\s*END_TASK\\b");

        Matcher matcher = taskPattern.matcher(normalized);

        int taskOrder = 1;

        while (matcher.find()) {

            String block = matcher.group(1).trim();

            if (block.isBlank()) {
                continue;
            }

            String title = extractField(block, "Title");
            String description = extractField(block, "Description");
            String expectedOutcome = extractField(block, "ExpectedOutcome");
            String requirements = extractField(block, "Requirements");
            String technology = extractField(block, "Technology");
            String difficulty = extractField(block, "Difficulty");
            String estimatedMinutes = extractField(block, "EstimatedMinutes");

            if (title.isBlank() || description.isBlank()) {

                log.warn(
                        "Skipping AI practical task because Title or Description is missing."
                );

                continue;
            }

            if (technology.isBlank() && trainingModule != null) {
                technology = safe(trainingModule.getSkillName());
            }

            if (technology.isBlank()) {
                technology = skillName;
            }

            PracticalTask task = PracticalTask.builder()

                    .employee(employee)

                    .learningPathItem(learningPathItem)

                    .trainingModule(trainingModule)

                    .title(limit(title, 250))

                    .skillName(limit(skillName, 150))

                    .description(description.trim())

                    .expectedOutcome(emptyToNull(expectedOutcome))

                    .requirements(emptyToNull(requirements))

                    .technology(
                            emptyToNull(limit(technology, 150))
                    )

                    .difficulty(parseDifficulty(difficulty))

                    .estimatedMinutes(parseMinutes(estimatedMinutes))

                    .skillScore(null)
                    .requiredSkillLevel(null)
                    .currentSkillLevel(null)
                    .gapPercentage(null)

                    .priority(
                            convertPriority(learningPathItem.getPriority())
                    )

                    .status(PracticalTask.TaskStatus.PENDING)

                    .progressPercentage(0)

                    .aiGenerated(true)

                    .taskOrder(taskOrder++)

                    .build();

            tasks.add(task);
        }

        if (tasks.isEmpty()) {

            log.error(
                    "Groq response was received but no practical tasks could be parsed."
            );

            log.error("AI response:\n{}", aiResponse);
        }

        return tasks;
    }

    // =========================================================
    // EXTRACT FIELD
    // =========================================================

    private String extractField(String block, String fieldName) {

        Pattern pattern = Pattern.compile(
                "(?ims)^\\s*"
                        + Pattern.quote(fieldName)
                        + "\\s*:\\s*"
                        + "(.*?)"
                        + "(?=^\\s*(?:Title|Description|ExpectedOutcome|Requirements|Technology|Difficulty|EstimatedMinutes)\\s*:|\\z)"
        );

        Matcher matcher = pattern.matcher(block);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return "";
    }

    // =========================================================
    // PARSE DIFFICULTY
    // =========================================================

    private PracticalTask.DifficultyLevel parseDifficulty(String value) {

        if (value == null || value.isBlank()) {
            return PracticalTask.DifficultyLevel.BEGINNER;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);

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
            LearningPathItem.Priority priority) {

        if (priority == null) {
            return PracticalTask.Priority.MEDIUM;
        }

        return switch (priority) {

            case HIGH -> PracticalTask.Priority.HIGH;

            case LOW -> PracticalTask.Priority.LOW;

            default -> PracticalTask.Priority.MEDIUM;
        };
    }

    // =========================================================
    // PARSE ESTIMATED MINUTES
    // =========================================================

    private Integer parseMinutes(String value) {

        if (value == null || value.isBlank()) {
            return 30;
        }

        Matcher matcher = Pattern.compile("\\d+").matcher(value);

        if (!matcher.find()) {
            return 30;
        }

        try {

            int minutes = Integer.parseInt(matcher.group());

            if (minutes <= 0) {
                return 30;
            }

            return Math.min(minutes, 480);

        } catch (NumberFormatException exception) {
            return 30;
        }
    }

    // =========================================================
    // SAFE STRING
    // =========================================================

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    // =========================================================
    // EMPTY TO NULL
    // =========================================================

    private String emptyToNull(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    // =========================================================
    // LIMIT STRING LENGTH
    // =========================================================

    private String limit(String value, int maxLength) {

        if (value == null) {
            return "";
        }

        String trimmed = value.trim();

        if (trimmed.length() <= maxLength) {
            return trimmed;
        }

        return trimmed.substring(0, maxLength).trim();
    }

    // =========================================================
    // REMOVE TRAILING SLASH
    // =========================================================

    private String removeTrailingSlash(String value) {

        if (value == null || value.isBlank()) {
            return "https://api.groq.com/openai/v1";
        }

        String result = value.trim();

        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }

        return result;
    }

    // =========================================================
    // SANITIZE URL
    // =========================================================

    private String sanitizeUrl(String url) {

        if (url == null || url.isBlank()) {
            return "Not configured";
        }

        return removeTrailingSlash(url)
                .replaceAll(
                        "(?i)(https?://)[^/@]+:[^/@]+@",
                        "$1***@"
                );
    }

    // =========================================================
    // RESPONSE PREVIEW
    // =========================================================

    private String preview(String value) {

        if (value == null || value.isBlank()) {
            return "[empty response]";
        }

        String result = value
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();

        return result.length() > 1000
                ? result.substring(0, 1000) + "..."
                : result;
    }
}
