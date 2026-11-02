
package com.capacitix.service;

import com.capacitix.entity.ChatMessage;
import com.capacitix.entity.User;

import java.util.List;

public interface AIChatService {

    /**
     * Sends an employee question to the AI and returns
     * the generated response.
     *
     * The conversation is saved in the database.
     *
     * @param employee logged-in employee
     * @param message employee question
     * @return AI-generated response
     */
    String chat(
            User employee,
            String message
    );


    /**
     * Gets the complete chat history of an employee.
     *
     * @param employee logged-in employee
     * @return list of chat messages
     */
    List<ChatMessage> getChatHistory(
            User employee
    );


    /**
     * Deletes the complete chat history of an employee.
     *
     * @param employee logged-in employee
     */
    void clearChatHistory(
            User employee
    );
}

