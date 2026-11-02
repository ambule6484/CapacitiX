package com.capacitix.controller;

import com.capacitix.dto.ChatRequest;
import com.capacitix.dto.ChatResponse;
import com.capacitix.entity.ChatMessage;
import com.capacitix.entity.User;
import com.capacitix.repository.UserRepository;
import com.capacitix.service.AIChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/employee/ai-assistant")
@PreAuthorize("hasRole('EMPLOYEE')")
public class AIChatController {

    private final AIChatService aiChatService;
    private final UserRepository userRepository;

    /**
     * Opens the AI Assistant page.
     */
    @GetMapping
    public String aiAssistantPage(
            Authentication authentication,
            Model model
    ) {

        User employee = getLoggedInEmployee(authentication);

        List<ChatMessage> chatHistory =
                aiChatService.getChatHistory(employee);

        model.addAttribute(
                "employee",
                employee
        );

        model.addAttribute(
                "chatHistory",
                chatHistory
        );

        return "employee/ai-assistant";
    }

    /**
     * Sends an employee message to the AI.
     */
    @PostMapping("/chat")
    @ResponseBody
    public ResponseEntity<ChatResponse> chat(
            Authentication authentication,
            @Valid @RequestBody ChatRequest request
    ) {

        try {

            User employee =
                    getLoggedInEmployee(authentication);

            String aiResponse =
                    aiChatService.chat(
                            employee,
                            request.getMessage()
                    );

            ChatResponse response =
                    ChatResponse.builder()
                            .success(true)
                            .response(aiResponse)
                            .error(null)
                            .build();

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {

            ChatResponse response =
                    ChatResponse.builder()
                            .success(false)
                            .response(null)
                            .error(e.getMessage())
                            .build();

            return ResponseEntity
                    .badRequest()
                    .body(response);

        } catch (Exception e) {

            System.err.println(
                    "AI Chat Controller Error: "
                            + e.getMessage()
            );

            ChatResponse response =
                    ChatResponse.builder()
                            .success(false)
                            .response(null)
                            .error(
                                    "Unable to process your request. "
                                            + "Please try again."
                            )
                            .build();

            return ResponseEntity
                    .internalServerError()
                    .body(response);
        }
    }

    /**
     * Returns the complete chat history of the
     * currently logged-in employee.
     */
    @GetMapping("/history")
    @ResponseBody
    public ResponseEntity<List<ChatMessage>> getChatHistory(
            Authentication authentication
    ) {

        User employee =
                getLoggedInEmployee(authentication);

        List<ChatMessage> history =
                aiChatService.getChatHistory(
                        employee
                );

        return ResponseEntity.ok(history);
    }

    /**
     * Clears the complete chat history of the
     * currently logged-in employee.
     */
    @DeleteMapping("/clear")
    @ResponseBody
    public ResponseEntity<ChatResponse> clearChatHistory(
            Authentication authentication
    ) {

        try {

            User employee =
                    getLoggedInEmployee(authentication);

            aiChatService.clearChatHistory(
                    employee
            );

            ChatResponse response =
                    ChatResponse.builder()
                            .success(true)
                            .response(
                                    "Chat history cleared successfully."
                            )
                            .error(null)
                            .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {

            System.err.println(
                    "Clear Chat History Error: "
                            + e.getMessage()
            );

            ChatResponse response =
                    ChatResponse.builder()
                            .success(false)
                            .response(null)
                            .error(
                                    "Unable to clear chat history."
                            )
                            .build();

            return ResponseEntity
                    .internalServerError()
                    .body(response);
        }
    }

    /**
     * Gets the currently logged-in employee.
     */
    private User getLoggedInEmployee(
            Authentication authentication
    ) {

        if (authentication == null
                || authentication.getName() == null) {

            throw new IllegalArgumentException(
                    "Employee authentication is required."
            );
        }

        String email =
                authentication.getName();

        User employee =
                userRepository
                        .findByEmail(email)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Logged-in employee was not found."
                                )
                        );

        if (employee.getRole()
                != User.Role.EMPLOYEE) {

            throw new IllegalArgumentException(
                    "Only employees can access the AI Assistant."
            );
        }

        return employee;
    }
}