package com.agent.dto.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;
// Removed @AllArgsConstructor

@Data
public class LlmRequest {
    private String model;
    private List<LlmMessage> messages;

    @JsonProperty("response_format")
    private ResponseFormat responseFormat = new ResponseFormat("json_object");

    // --- FIX: Add a manual constructor for the two required fields ---
    public LlmRequest(String model, List<LlmMessage> messages) {
        this.model = model;
        this.messages = messages;
    }

    @Data
    public static class ResponseFormat {
        private String type;

        // --- Add a manual constructor for clarity ---
        public ResponseFormat(String type) {
            this.type = type;
        }
    }
}