package com.agent.dto.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmResponse {
    private List<Choice> choices;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        private LlmResponseMessage message;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LlmResponseMessage {
        private String role;
        private String content;
    }
}