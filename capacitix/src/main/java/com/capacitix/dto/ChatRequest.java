
package com.capacitix.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    /**
     * Message/question sent by the employee.
     */
    @NotBlank(message = "Message cannot be empty.")
    @Size(
            max = 5000,
            message = "Message cannot exceed 5000 characters."
    )
    private String message;
}

