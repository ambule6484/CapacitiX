
package com.capacitix.service;

import com.capacitix.entity.ChatMessage;
import com.capacitix.entity.User;
import com.capacitix.repository.ChatMessageRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import jakarta.annotation.PostConstruct;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AIChatServiceImpl implements AIChatService {

    private final ChatMessageRepository chatMessageRepository;

    private final ObjectMapper objectMapper;

    // =========================================================
    // GROQ CONFIGURATION
    // =========================================================

    @Value("${GROQ_API_KEY:${groq.api.key:}}")
    private String groqApiKey;

    @Value("${groq.api.url:https://api.groq.com/openai/v1}")
    private String groqBaseUrl;

    @Value("${groq.model:openai/gpt-oss-120b}")
    private String groqModel;

    @Value("${groq.chat.timeout:60000}")
    private long groqTimeout;

    @Value("${groq.connect-timeout:10000}")
    private long connectTimeout;

    @Value("${groq.chat.temperature:0.4}")
    private double temperature;

    @Value("${groq.chat.max-tokens:1200}")
    private int maxTokens;

    private volatile RestClient restClient;

    // =========================================================
    // INITIALIZE GROQ
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
                                        : groqApiKey.trim()
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
        log.info("CAPACITIX AI CHAT INITIALIZED");
        log.info("Provider: Groq");
        log.info("Model: {}", groqModel);
        log.info("Timeout: {} ms", groqTimeout);
        log.info("========================================");
    }

    // =========================================================
    // CHAT WITH AI
    // =========================================================

    @Override
    public String chat(
            User employee,
            String message
    ) {

        // -----------------------------------------------------
        // VALIDATE EMPLOYEE
        // -----------------------------------------------------

        if (employee == null) {

            throw new IllegalArgumentException(
                    "Employee cannot be null."
            );
        }

        // -----------------------------------------------------
        // SECURITY CHECK
        // -----------------------------------------------------

        if (employee.getRole() != User.Role.EMPLOYEE) {

            throw new IllegalArgumentException(
                    "Only employees can use the AI Assistant."
            );
        }

        // -----------------------------------------------------
        // VALIDATE MESSAGE
        // -----------------------------------------------------

        if (message == null || message.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Message cannot be empty."
            );
        }

        String userMessage = message.trim();

        // -----------------------------------------------------
        // LIMIT MESSAGE SIZE
        // -----------------------------------------------------

        if (userMessage.length() > 5000) {

            throw new IllegalArgumentException(
                    "Message is too long. Please keep your question under 5000 characters."
            );
        }

        // -----------------------------------------------------
        // CHECK GROQ API KEY
        // -----------------------------------------------------

        if (groqApiKey == null || groqApiKey.isBlank()) {

            log.error("GROQ_API_KEY is not configured.");

            throw new RuntimeException(
                    "AI service is not configured. Please contact the administrator."
            );
        }

        // =====================================================
        // SAVE USER MESSAGE
        // =====================================================

        ChatMessage userChatMessage =
                ChatMessage.builder()
                        .employee(employee)
                        .senderType(ChatMessage.SenderType.USER)
                        .message(userMessage)
                        .build();

        chatMessageRepository.save(userChatMessage);

        // =====================================================
        // BUILD AI PROMPT
        // =====================================================

        String prompt = buildPrompt(
                employee,
                userMessage
        );

        // =====================================================
        // CALL GROQ
        // =====================================================

        String aiResponse;

        try {

            log.info(
                    "CapacitiX Groq AI request started | Employee ID: {}",
                    employee.getId()
            );

            long startTime = System.nanoTime();

            aiResponse = callGroq(prompt);

            long elapsedMillis =
                    (System.nanoTime() - startTime) / 1_000_000;

            log.info(
                    "CapacitiX Groq response received | Employee ID: {} | Time: {} ms",
                    employee.getId(),
                    elapsedMillis
            );

        } catch (Exception exception) {

            log.error(
                    "CapacitiX Groq AI request failed | Employee ID: {} | Error: {}",
                    employee.getId(),
                    exception.getMessage(),
                    exception
            );

            throw new RuntimeException(
                    "Unable to connect to the CapacitiX AI service. "
                            + "Please try again later.",
                    exception
            );
        }

        // =====================================================
        // VALIDATE AI RESPONSE
        // =====================================================

        if (aiResponse == null || aiResponse.isBlank()) {

            log.error(
                    "Groq returned an empty response | Employee ID: {}",
                    employee.getId()
            );

            throw new RuntimeException(
                    "The AI service returned an empty response."
            );
        }

        aiResponse = aiResponse.trim();

        // =====================================================
        // SAVE AI RESPONSE
        // =====================================================

        ChatMessage aiChatMessage =
                ChatMessage.builder()
                        .employee(employee)
                        .senderType(ChatMessage.SenderType.AI)
                        .message(aiResponse)
                        .build();

        chatMessageRepository.save(aiChatMessage);

        log.info(
                "AI chat conversation saved successfully | Employee ID: {}",
                employee.getId()
        );

        // =====================================================
        // RETURN RESPONSE
        // =====================================================

        return aiResponse;
    }

    // =========================================================
    // CALL GROQ API
    // =========================================================

    private String callGroq(String prompt) {

        try {

            RestClient client = getRestClient();

            Map<String, Object> systemMessage =
                    Map.of(
                            "role",
                            "system",
                            "content",
                            """
                            You are Capacitix AI Assistant.

                            You are an intelligent employee learning
                            and development assistant.

                            Give accurate, practical and beginner-friendly
                            answers. Do not invent employee-specific data.
                            Follow the system instructions in the user prompt.
                            """
                    );

            Map<String, Object> userMessage =
                    Map.of(
                            "role",
                            "user",
                            "content",
                            prompt
                    );

            Map<String, Object> request =
                    new LinkedHashMap<>();

            request.put("model", groqModel);

            request.put(
                    "messages",
                    List.of(systemMessage, userMessage)
            );

            request.put(
                    "temperature",
                    Math.max(
                            0.0,
                            Math.min(0.7, temperature)
                    )
            );

            request.put(
                    "max_tokens",
                    Math.max(
                            200,
                            Math.min(3000, maxTokens)
                    )
            );

            request.put("top_p", 0.9);

            log.debug(
                    "Sending AI chat request to Groq model {}",
                    groqModel
            );

            String rawResponse = client.post()
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

            if (root.has("error")
                    && !root.path("error").isNull()) {

                throw new RuntimeException(
                        "Groq API error: "
                                + root.path("error").toString()
                );
            }

            JsonNode choices = root.path("choices");

            if (!choices.isArray()
                    || choices.isEmpty()) {

                throw new RuntimeException(
                        "Groq returned no completion choices."
                );
            }

            JsonNode messageNode = choices.get(0)
                    .path("message");

            String response =
                    messageNode.path("content").asText("");

            if (response.isBlank()) {

                throw new RuntimeException(
                        "Groq returned empty message content."
                );
            }

            JsonNode usage = root.path("usage");

            if (usage.isObject()) {

                log.info(
                        "Groq chat tokens | Prompt: {} | Completion: {} | Total: {}",
                        usage.path("prompt_tokens").asInt(-1),
                        usage.path("completion_tokens").asInt(-1),
                        usage.path("total_tokens").asInt(-1)
                );
            }

            return response.trim();

        } catch (HttpStatusCodeException exception) {

            log.error(
                    "Groq HTTP error | Status: {} | Response: {}",
                    exception.getStatusCode(),
                    safePreview(
                            exception.getResponseBodyAsString()
                    )
            );

            throw new RuntimeException(
                    "Groq API request failed: "
                            + exception.getStatusCode(),
                    exception
            );

        } catch (ResourceAccessException exception) {

            log.error(
                    "Groq connection or timeout error. Timeout: {} ms",
                    groqTimeout,
                    exception
            );

            throw new RuntimeException(
                    "Groq AI request timed out. Please try again.",
                    exception
            );

        } catch (Exception exception) {

            log.error(
                    "Groq chat generation failed: {}",
                    exception.getMessage(),
                    exception
            );

            throw new RuntimeException(
                    "Unable to generate an AI response.",
                    exception
            );
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
                        .baseUrl(
                                removeTrailingSlash(groqBaseUrl)
                        )
                        .requestFactory(factory)
                        .defaultHeader(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + groqApiKey.trim()
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
    // GET CHAT HISTORY
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessage> getChatHistory(
            User employee
    ) {

        if (employee == null) {

            throw new IllegalArgumentException(
                    "Employee cannot be null."
            );
        }

        if (employee.getRole() != User.Role.EMPLOYEE) {

            throw new IllegalArgumentException(
                    "Only employees can access AI chat history."
            );
        }

        return chatMessageRepository
                .findByEmployeeOrderByCreatedAtAsc(employee);
    }

    // =========================================================
    // CLEAR CHAT HISTORY
    // =========================================================

    @Override
    public void clearChatHistory(
            User employee
    ) {

        if (employee == null) {

            throw new IllegalArgumentException(
                    "Employee cannot be null."
            );
        }

        if (employee.getRole() != User.Role.EMPLOYEE) {

            throw new IllegalArgumentException(
                    "Only employees can clear AI chat history."
            );
        }

        chatMessageRepository.deleteByEmployee(employee);

        log.info(
                "AI chat history cleared | Employee ID: {}",
                employee.getId()
        );
    }

    // =========================================================
    // BUILD AI PROMPT
    // =========================================================

    private String buildPrompt(
            User employee,
            String userMessage
    ) {

        String employeeName =
                safe(employee.getFullName());

        String department =
                safe(employee.getDepartment());

        String designation =
                safe(employee.getDesignation());

        return """
                You are Capacitix AI Assistant.

                You are an intelligent learning and employee
                development assistant inside the Capacitix
                employee platform.

                =================================================
                YOUR ROLE
                =================================================

                Help employees with:

                - Programming
                - Java
                - Python
                - C++
                - SQL
                - Spring Boot
                - REST APIs
                - Software development
                - Data structures and algorithms
                - Object-oriented programming
                - Assessments
                - Skill development
                - Learning paths
                - Training
                - Career-related technical learning

                =================================================
                IMPORTANT RULES
                =================================================

                1. Give simple, clear and practical explanations.

                2. Use examples when they help the employee
                   understand the concept.

                3. If the employee asks a programming question,
                   provide correct and practical code.

                4. Explain important parts of the code.

                5. Prefer beginner-friendly explanations unless
                   the employee clearly asks for advanced details.

                6. Never intentionally provide incorrect
                   information.

                7. Never invent assessment scores.

                8. Never invent employee skill gaps.

                9. Never invent learning progress.

                10. Never claim that an employee completed a
                    training module unless actual information
                    has been provided.

                11. If employee-specific information is not
                    available, clearly say that you do not have
                    that information.

                12. Keep the response focused on the question.

                13. Encourage practical learning and hands-on
                    practice.

                14. If the employee asks about a concept,
                    explain:
                    - What it is
                    - Why it is used
                    - Simple example
                    - Practical use

                15. If the employee asks for code, provide
                    complete working code when reasonably possible.

                =================================================
                EMPLOYEE INFORMATION
                =================================================

                Name:
                %s

                Department:
                %s

                Designation:
                %s

                =================================================
                IMPORTANT LIMITATION
                =================================================

                You currently have access only to the employee
                information provided below.

                Do not assume assessment results, skill gaps,
                learning path details, training progress or
                competency information unless they are explicitly
                provided in the prompt.

                If the employee asks something such as:

                "Explain my learning path"

                "What are my skill gaps?"

                "What training should I complete?"

                "What was my assessment score?"

                and the required employee-specific information
                is not available, explain that the information
                is currently unavailable instead of inventing it.

                =================================================
                EMPLOYEE QUESTION
                =================================================

                %s

                =================================================
                RESPONSE
                =================================================

                Answer the employee's question now.
                """.formatted(
                employeeName,
                department,
                designation,
                userMessage
        );
    }

    // =========================================================
    // SAFE STRING
    // =========================================================

    private String safe(String value) {

        if (value == null || value.isBlank()) {
            return "Not specified";
        }

        return value.trim();
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
