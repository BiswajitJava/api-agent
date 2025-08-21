package com.agent.dto.llm;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LlmMessage {
    private String role; // "system" or "user"
    private String content;
}