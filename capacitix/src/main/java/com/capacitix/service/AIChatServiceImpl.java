
package com.capacitix.service;

import com.capacitix.entity.ChatMessage;
import com.capacitix.entity.User;
import com.capacitix.repository.ChatMessageRepository;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AIChatServiceImpl implements AIChatService {

    private final ChatMessageRepository chatMessageRepository;

    private final RestClient.Builder restClientBuilder;

    // =========================================================
    // GROQ CONFIGURATION
    // =========================================================

    @Value("${groq.api-key:}")
    private String groqApiKey;

    @Value("${groq.base-url:https://api.groq.com/openai/v1}")
    private String groqBaseUrl;

    @Value("${groq.model:openai/gpt-oss-120b}")
    private String groqModel;

    @Value("${groq.temperature:0.2}")
    private double groqTemperature;

    @Value("${groq.max-tokens:4096}")
    private int groqMaxTokens;

    private RestClient restClient;

    // =========================================================
    // INITIALIZE GROQ REST CLIENT
    // =========================================================

    @PostConstruct
    public void initialize() {

        restClient = restClientBuilder
                .baseUrl(groqBaseUrl)
                .defaultHeader(
                        "Authorization",
                        "Bearer " + (groqApiKey == null ? "" : groqApiKey)
                )
                .defaultHeader(
                        "Content-Type",
                        MediaType.APPLICATION_JSON_VALUE
                )
                .build();

        log.info(
                "CapacitiX AI Chat initialized | Provider: Groq | Model: {}",
                groqModel
        );
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
            log.error("Groq API key is not configured.");

            throw new IllegalStateException(
                    "The AI service is not configured. Please contact the administrator."
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
        // BUILD SYSTEM PROMPT
        // =====================================================

        String systemPrompt = buildSystemPrompt(employee);

        // =====================================================
        // CALL GROQ API
        // =====================================================

        String aiResponse;

        try {

            log.info(
                    "CapacitiX AI Assistant request started | Employee ID: {} | Provider: Groq | Model: {}",
                    employee.getId(),
                    groqModel
            );

            long startTime = System.nanoTime();

            Map<String, Object> requestBody = Map.of(
                    "model", groqModel,
                    "messages", List.of(
                            Map.of(
                                    "role", "system",
                                    "content", systemPrompt
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", userMessage
                            )
                    ),
                    "temperature", groqTemperature,
                    "max_completion_tokens", groqMaxTokens
            );

            JsonNode response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);

            long elapsedMillis =
                    (System.nanoTime() - startTime) / 1_000_000;

            // -------------------------------------------------
            // EXTRACT RESPONSE CONTENT
            // -------------------------------------------------

            if (response == null
                    || !response.has("choices")
                    || !response.get("choices").isArray()
                    || response.get("choices").isEmpty()) {

                throw new IllegalStateException(
                        "Groq returned an invalid or empty response."
                );
            }

            JsonNode messageNode = response
                    .path("choices")
                    .get(0)
                    .path("message");

            aiResponse = messageNode
                    .path("content")
                    .asText("");

            if (aiResponse.isBlank()) {
                throw new IllegalStateException(
                        "Groq returned empty message content."
                );
            }

            log.info(
                    "CapacitiX AI response received successfully | Employee ID: {} | Time: {} ms",
                    employee.getId(),
                    elapsedMillis
            );

        } catch (RestClientResponseException exception) {

            log.error(
                    "Groq API returned an error | Employee ID: {} | HTTP Status: {} | Response: {}",
                    employee.getId(),
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString(),
                    exception
            );

            throw new RuntimeException(
                    "The AI service is temporarily unavailable. Please try again later.",
                    exception
            );

        } catch (RestClientException exception) {

            log.error(
                    "Unable to connect to Groq API | Employee ID: {} | Error: {}",
                    employee.getId(),
                    exception.getMessage(),
                    exception
            );

            throw new RuntimeException(
                    "Unable to connect to the CapacitiX AI service. Please try again later.",
                    exception
            );

        } catch (Exception exception) {

            log.error(
                    "CapacitiX AI Assistant request failed | Employee ID: {} | Error: {}",
                    employee.getId(),
                    exception.getMessage(),
                    exception
            );

            throw new RuntimeException(
                    "Unable to generate an AI response. Please try again later.",
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

        return chatMessageRepository
                .findByEmployeeOrderByCreatedAtAsc(
                        employee
                );
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

        chatMessageRepository.deleteByEmployee(
                employee
        );

        log.info(
                "AI chat history cleared | Employee ID: {}",
                employee.getId()
        );
    }

    // =========================================================
    // BUILD SYSTEM PROMPT
    // =========================================================

    private String buildSystemPrompt(
            User employee
    ) {

        String employeeName =
                safe(employee.getFullName());

        String department =
                safe(employee.getDepartment());

        String designation =
                safe(employee.getDesignation());

        return """
                You are CapacitiX AI Assistant.

                You are an intelligent learning and employee
                development assistant inside the CapacitiX
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
                provided in the conversation.

                If the employee asks something such as:

                "Explain my learning path"

                "What are my skill gaps?"

                "What training should I complete?"

                "What was my assessment score?"

                and the required employee-specific information
                is not available, explain that the information
                is currently unavailable instead of inventing it.

                Answer the employee's latest question clearly
                and practically.
                """.formatted(
                employeeName,
                department,
                designation
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
}
