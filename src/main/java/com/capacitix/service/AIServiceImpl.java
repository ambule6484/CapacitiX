
package com.capacitix.service;

import com.capacitix.dto.LearningPathItem;
import com.capacitix.dto.LearningPathResponse;
import com.capacitix.entity.User;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import jakarta.annotation.PostConstruct;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIServiceImpl implements AIService {

    // =========================================================
    // GROQ CONFIGURATION
    // =========================================================

    @Value("${GROQ_API_KEY:${groq.api.key:}}")
    private String groqApiKey;

    @Value("${groq.api.url:https://api.groq.com/openai/v1}")
    private String groqBaseUrl;

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String groqModel;

    @Value("${groq.timeout:60000}")
    private long groqTimeout;

    @Value("${groq.connect-timeout:10000}")
    private long connectTimeout;

    @Value("${groq.temperature:0.3}")
    private double groqTemperature;

    @Value("${groq.max-tokens:500}")
    private int groqMaxTokens;

    private final ObjectMapper objectMapper;

    private RestClient restClient;

    // =========================================================
    // INITIALIZE GROQ CLIENT
    // =========================================================

    @PostConstruct
    public void initializeGroqClient() {

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(
                Duration.ofMillis(
                        Math.max(connectTimeout, 1000)
                )
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
                        "Bearer " + (
                                groqApiKey == null
                                        ? ""
                                        : groqApiKey
                        )
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
        log.info("CAPACITIX AI SERVICE INITIALIZED");
        log.info("Provider: Groq");
        log.info("Model: {}", groqModel);
        log.info("Timeout: {} ms", groqTimeout);
        log.info("Strategy: SINGLE REQUEST");
        log.info("========================================");
    }

    // =========================================================
    // GENERATE LEARNING PATH
    // =========================================================

    @Override
    public LearningPathResponse generateLearningPath(
            User employee,
            Object skillGaps
    ) {

        if (employee == null) {
            throw new IllegalArgumentException(
                    "Employee cannot be null."
            );
        }

        List<SkillAnalysisService.SkillResult> gaps =
                getSkillGaps(skillGaps);

        // No gaps: do not call Groq.
        if (gaps.isEmpty()) {

            log.info(
                    "No skill gaps found for employee: {}",
                    employee.getEmail()
            );

            return buildLearningPathResponse(
                    employee,
                    gaps,
                    "Your assessment results indicate that no major "
                            + "skill gaps were identified. Continue "
                            + "practicing your existing skills."
            );
        }

        String prompt = buildLearningPathPrompt(
                employee,
                gaps
        );

        String aiResponse;

        try {

            log.info(
                    "Generating learning path | Employee={} | Gaps={} | Provider=Groq",
                    employee.getEmail(),
                    gaps.size()
            );

            long startTime = System.nanoTime();

            aiResponse = callGroq(prompt);

            long elapsedMillis =
                    (System.nanoTime() - startTime) / 1_000_000;

            if (aiResponse == null || aiResponse.isBlank()) {

                log.warn(
                        "Groq returned an empty learning-path response."
                );

                aiResponse = getFallbackRecommendation();

            } else {

                log.info(
                        "Groq learning-path generation completed in {} ms.",
                        elapsedMillis
                );
            }

        } catch (Exception exception) {

            log.error(
                    "Groq learning-path generation failed for employee {}: {}",
                    employee.getEmail(),
                    exception.getMessage(),
                    exception
            );

            // Preserve fallback behaviour.
            aiResponse = getFallbackRecommendation();
        }

        return buildLearningPathResponse(
                employee,
                gaps,
                aiResponse
        );
    }

    // =========================================================
    // GROQ API CALL
    // =========================================================

    private String callGroq(String prompt) {

        if (groqApiKey == null || groqApiKey.isBlank()) {

            throw new IllegalStateException(
                    "Groq API key is missing. Configure GROQ_API_KEY."
            );
        }

        Map<String, Object> systemMessage = Map.of(
                "role",
                "system",
                "content",
                """
                You are CapacitiX, an intelligent enterprise learning assistant.

                Analyze employee assessment skill gaps carefully.
                Give accurate, concise and professional recommendations.
                Use only the provided information.
                Never invent employee scores or competencies.
                """
        );

        Map<String, Object> userMessage = Map.of(
                "role",
                "user",
                "content",
                prompt
        );

        Map<String, Object> request = new LinkedHashMap<>();

        request.put("model", groqModel);
        request.put(
                "messages",
                List.of(systemMessage, userMessage)
        );
        request.put(
                "temperature",
                Math.max(0.0, Math.min(0.5, groqTemperature))
        );
        request.put(
                "max_tokens",
                Math.max(100, Math.min(1000, groqMaxTokens))
        );
        request.put("top_p", 0.9);

        try {

            String rawResponse = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);

            if (rawResponse == null || rawResponse.isBlank()) {

                throw new RuntimeException(
                        "Groq returned an empty HTTP response."
                );
            }

            JsonNode root =
                    objectMapper.readTree(rawResponse);

            if (root == null || !root.isObject()) {

                throw new RuntimeException(
                        "Invalid Groq response."
                );
            }

            if (root.has("error")
                    && !root.path("error").isNull()) {

                throw new RuntimeException(
                        "Groq API error: "
                                + root.path("error").toString()
                );
            }

            JsonNode choices = root.path("choices");

            if (!choices.isArray() || choices.isEmpty()) {

                throw new RuntimeException(
                        "Groq returned no completion choices."
                );
            }

            JsonNode choice = choices.get(0);

            String content = choice
                    .path("message")
                    .path("content")
                    .asText("");

            if (content.isBlank()) {

                throw new RuntimeException(
                        "Groq returned empty generated content."
                );
            }

            JsonNode usage = root.path("usage");

            if (usage.isObject()) {

                log.info(
                        "Learning path token usage: prompt={}, completion={}, total={}",
                        usage.path("prompt_tokens").asInt(-1),
                        usage.path("completion_tokens").asInt(-1),
                        usage.path("total_tokens").asInt(-1)
                );
            }

            return content.trim();

        } catch (HttpStatusCodeException exception) {

            log.error(
                    "Groq HTTP error: Status={}, Response={}",
                    exception.getStatusCode(),
                    safePreview(
                            exception.getResponseBodyAsString()
                    )
            );

            throw new RuntimeException(
                    "Groq API request failed. HTTP "
                            + exception.getStatusCode().value()
                            + ": "
                            + groqErrorMessage(
                                    exception.getResponseBodyAsString()
                            ),
                    exception
            );

        } catch (ResourceAccessException exception) {

            throw new RuntimeException(
                    "Groq request timed out or connection failed. "
                            + "Configured timeout: "
                            + groqTimeout + " ms.",
                    exception
            );

        } catch (RuntimeException exception) {

            throw exception;

        } catch (Exception exception) {

            throw new RuntimeException(
                    "Unable to process Groq response: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    // =========================================================
    // GROQ ERROR MESSAGE
    // =========================================================

    private String groqErrorMessage(String response) {

        if (response == null || response.isBlank()) {
            return "No error details returned.";
        }

        try {

            JsonNode root =
                    objectMapper.readTree(response);

            JsonNode error = root.path("error");

            if (error.isObject()) {

                return error.path("message")
                        .asText("Unknown Groq API error.");
            }

            if (error.isTextual()) {
                return error.asText();
            }

        } catch (Exception ignored) {
            // Use safe preview below.
        }

        return safePreview(response);
    }

    // =========================================================
    // GET SKILL GAPS
    // =========================================================

    private List<SkillAnalysisService.SkillResult> getSkillGaps(
            Object skillGaps
    ) {

        if (!(skillGaps instanceof
                SkillAnalysisService.SkillAnalysisResult analysis)) {

            return new ArrayList<>();
        }

        if (analysis.skillGaps() == null) {
            return new ArrayList<>();
        }

        return analysis.skillGaps();
    }

    // =========================================================
    // BUILD COMPACT AI PROMPT
    // =========================================================

    private String buildLearningPathPrompt(
            User employee,
            List<SkillAnalysisService.SkillResult> gaps
    ) {

        StringBuilder prompt = new StringBuilder(1000);

        prompt.append("""
                Analyze the actual assessment skill gaps provided below.

                RULES:
                - Use only the provided assessment data.
                - Never invent scores or competencies.
                - Prioritize HIGH gaps before MEDIUM gaps.
                - Give practical and achievable advice.
                - Do not recommend unrelated skills.
                - Keep recommendations concise.
                - Do not generate a separate learning path.
                - Do not repeat employee personal information.

                EMPLOYEE:
                """);

        prompt.append("\nName: ")
                .append(safe(employee.getFullName()));

        prompt.append("\nDepartment: ")
                .append(safe(employee.getDepartment()));

        prompt.append("\nDesignation: ")
                .append(safe(employee.getDesignation()));

        prompt.append("\n\nACTUAL SKILL GAPS:\n");

        for (SkillAnalysisService.SkillResult skill : gaps) {

            prompt.append("- Skill: ")
                    .append(skill.competencyName());

            prompt.append(" | Score: ")
                    .append(skill.percentage())
                    .append("%");

            prompt.append(" | Priority: ")
                    .append(skill.gap());

            prompt.append(" | Current Level: ")
                    .append(skill.currentLevel());

            prompt.append(" | Required Level: ")
                    .append(skill.requiredLevel());

            if (skill.category() != null
                    && !skill.category().isBlank()) {

                prompt.append(" | Category: ")
                        .append(skill.category());
            }

            prompt.append("\n");
        }

        prompt.append("""
                
                PROVIDE:
                1. Overall assessment observation.
                2. Most important improvement area.
                3. Recommended learning strategy.
                4. Practical advice for improving competency.

                Keep the complete response within 100 words.
                Use simple professional English.
                Do not use Markdown tables.
                """);

        return prompt.toString();
    }

    // =========================================================
    // BUILD LEARNING PATH RESPONSE
    // =========================================================

    private LearningPathResponse buildLearningPathResponse(
            User employee,
            List<SkillAnalysisService.SkillResult> gaps,
            String aiResponse
    ) {

        List<LearningPathItem> learningItems =
                new ArrayList<>();

        int order = 1;

        for (SkillAnalysisService.SkillResult skill : gaps) {

            String practicalTask =
                    createPracticalTask(
                            skill.competencyName()
                    );

            String duration =
                    calculateDuration(
                            skill.percentage()
                    );

            String outcome =
                    createExpectedOutcome(
                            skill.competencyName()
                    );

            List<String> topics =
                    createTopics(
                            skill.competencyName(),
                            skill.category()
                    );

            LearningPathItem item =
                    LearningPathItem.builder()

                            .skillName(
                                    skill.competencyName()
                            )

                            .priority(
                                    skill.gap()
                            )

                            .currentScore(
                                    (double) skill.percentage()
                            )

                            .gapReason(
                                    buildGapReason(skill)
                            )

                            .topics(
                                    topics
                            )

                            .practicalTask(
                                    practicalTask
                            )

                            .estimatedDuration(
                                    duration
                            )

                            .expectedOutcome(
                                    outcome
                            )

                            .learningOrder(
                                    order++
                            )

                            .build();

            learningItems.add(item);
        }

        // =====================================================
        // COUNT PRIORITIES
        // =====================================================

        int highPriority = 0;
        int mediumPriority = 0;

        for (LearningPathItem item : learningItems) {

            if ("HIGH".equalsIgnoreCase(
                    item.getPriority()
            )) {

                highPriority++;

            } else if ("MEDIUM".equalsIgnoreCase(
                    item.getPriority()
            )) {

                mediumPriority++;
            }
        }

        // =====================================================
        // FINAL RESPONSE
        // =====================================================

        return LearningPathResponse.builder()

                .employeeName(
                        employee.getFullName()
                )

                .employeeRole(
                        employee.getDesignation()
                )

                .totalSkillGaps(
                        learningItems.size()
                )

                .highPriorityGaps(
                        highPriority
                )

                .mediumPriorityGaps(
                        mediumPriority
                )

                .learningPath(
                        learningItems
                )

                .overallRecommendation(
                        aiResponse
                )

                .expectedOutcome(
                        learningItems.isEmpty()

                                ? "No major skill gaps were identified."

                                : "Complete the recommended learning "
                                + "topics and practical tasks to improve "
                                + "the identified competency gaps."
                )

                .build();
    }

    // =========================================================
    // GAP REASON
    // =========================================================

    private String buildGapReason(
            SkillAnalysisService.SkillResult skill
    ) {

        return "Your assessment score for "
                + skill.competencyName()
                + " is "
                + skill.percentage()
                + "%. Your current level is "
                + skill.currentLevel()
                + ", while the required level is "
                + skill.requiredLevel()
                + ".";
    }

    // =========================================================
    // TOPICS
    // =========================================================

    private List<String> createTopics(
            String competencyName,
            String category
    ) {

        List<String> topics = new ArrayList<>();

        topics.add(
                competencyName + " fundamentals"
        );

        topics.add(
                "Important concepts of " + competencyName
        );

        topics.add(
                "Common problems and practical examples"
        );

        topics.add(
                "Hands-on practice with " + competencyName
        );

        if (category != null && !category.isBlank()) {

            topics.add(
                    "Practical " + category + " use cases"
            );
        }

        return topics;
    }

    // =========================================================
    // PRACTICAL TASK
    // =========================================================

    private String createPracticalTask(
            String competencyName
    ) {

        return "Build a small practical project or "
                + "complete hands-on exercises using "
                + competencyName
                + " and demonstrate the result.";
    }

    // =========================================================
    // DURATION
    // =========================================================

    private String calculateDuration(
            int percentage
    ) {

        if (percentage < 40) {
            return "7-14 days";
        }

        if (percentage < 60) {
            return "5-7 days";
        }

        return "3-5 days";
    }

    // =========================================================
    // EXPECTED OUTCOME
    // =========================================================

    private String createExpectedOutcome(
            String competencyName
    ) {

        return "Improve practical understanding of "
                + competencyName
                + " and apply it confidently in "
                + "real-world tasks.";
    }

    // =========================================================
    // FALLBACK RECOMMENDATION
    // =========================================================

    private String getFallbackRecommendation() {

        return "Focus on the identified competency gaps. "
                + "Start with high-priority skills, study "
                + "the recommended topics, complete practical "
                + "tasks and reassess your progress regularly.";
    }

    // =========================================================
    // SAFE STRING
    // =========================================================

    private String safe(String value) {

        if (value == null || value.isBlank()) {
            return "Not specified";
        }

        return value;
    }

    // =========================================================
    // URL HELPER
    // =========================================================

    private String removeTrailingSlash(String url) {

        if (url == null || url.isBlank()) {
            return "https://api.groq.com/openai/v1";
        }

        String result = url.trim();

        while (result.endsWith("/")) {
            result = result.substring(
                    0,
                    result.length() - 1
            );
        }

        return result;
    }

    // =========================================================
    // SAFE RESPONSE PREVIEW
    // =========================================================

    private String safePreview(String value) {

        if (value == null || value.isBlank()) {
            return "[empty response]";
        }

        String result = value
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();

        return result.length() > 500
                ? result.substring(0, 500) + "..."
                : result;
    }
}
