
package com.capacitix.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /**
     * AI-generated response.
     */
    private String response;


    /**
     * Indicates whether the response was generated
     * successfully.
     */
    private boolean success;


    /**
     * Optional error message.
     */
    private String error;
}