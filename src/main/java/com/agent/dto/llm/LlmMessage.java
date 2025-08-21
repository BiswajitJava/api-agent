package com.agent.dto.llm;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Represents a single message in a conversation with a Large Language Model (LLM).
 * This class is a simple Data Transfer Object (DTO) used to structure the input
 * sent to the LLM, clearly defining the role of the message's author and its content.
 * <p>
 * Lombok annotations are used to reduce boilerplate code.
 */
@Data
@AllArgsConstructor
public class LlmMessage {

    /**
     * The role of the entity sending the message.
     * Typically, this will be "system" for initial instructions or context,
     * or "user" for prompts and questions.
     */
    private String role;

    /**
     * The actual text content of the message.
     */
    private String content;
}