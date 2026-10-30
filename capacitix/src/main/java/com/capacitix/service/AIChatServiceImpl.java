package com.capacitix.service;

import com.capacitix.entity.ChatMessage;
import com.capacitix.entity.User;
import com.capacitix.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AIChatServiceImpl implements AIChatService {

    private final OllamaChatModel ollamaChatModel;

    private final ChatMessageRepository chatMessageRepository;


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
        // CALL OLLAMA
        // =====================================================

        String aiResponse;

        try {

            System.out.println(
                    "================================================="
            );

            System.out.println(
                    "Capacitix AI Assistant"
            );

            System.out.println(
                    "Employee: " + employee.getEmail()
            );

            System.out.println(
                    "Question: " + userMessage
            );

            System.out.println(
                    "Sending request to Ollama..."
            );

            System.out.println(
                    "================================================="
            );


            aiResponse = ollamaChatModel.call(prompt);


            System.out.println(
                    "Capacitix AI response received successfully."
            );


        } catch (Exception e) {

            // -------------------------------------------------
            // LOG COMPLETE TECHNICAL ERROR
            // -------------------------------------------------

            System.err.println(
                    "================================================="
            );

            System.err.println(
                    "CAPACITIX AI ASSISTANT ERROR"
            );

            System.err.println(
                    "Ollama request failed."
            );

            System.err.println(
                    "Employee: " + employee.getEmail()
            );

            System.err.println(
                    "Question: " + userMessage
            );

            System.err.println(
                    "Error: " + e.getMessage()
            );

            System.err.println(
                    "================================================="
            );

            e.printStackTrace();


            // -------------------------------------------------
            // IMPORTANT
            // -------------------------------------------------
            // Do NOT return a fake successful AI response.
            // Throw the exception so AIChatController can
            // return an error response to the frontend.

            throw new RuntimeException(
                    "Unable to connect to the Capacitix AI service. "
                            + "Please make sure Ollama is running.",
                    e
            );
        }


        // =====================================================
        // VALIDATE AI RESPONSE
        // =====================================================

        if (aiResponse == null
                || aiResponse.trim().isEmpty()) {

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


        // =====================================================
        // CAPACITIX AI SYSTEM PROMPT
        // =====================================================

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

    private String safe(
            String value
    ) {

        if (value == null || value.isBlank()) {
            return "Not specified";
        }

        return value.trim();
    }
}