package com.agent.dto.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/**
 * Represents a request payload to be sent to a Large Language Model (LLM).
 * This class encapsulates all the necessary information for the LLM to process a request,
 * including the model to use, the conversational history, and the desired response format.
 * <p>
 * Lombok's {@code @Data} annotation is used to generate standard boilerplate code like
 * getters, setters, {@code toString()}, {@code equals()}, and {@code hashCode()}.
 */
@Data
public class LlmRequest {

    /**
     * The identifier of the LLM model to be used for this request (e.g., "gpt-4-turbo").
     */
    private String model;

    /**
     * A list of {@link LlmMessage} objects representing the conversation history.
     * This should include system instructions and user prompts in chronological order.
     */
    private List<LlmMessage> messages;

    /**
     * Specifies the format of the response from the LLM.
     * By default, it is configured to expect a JSON object.
     */
    @JsonProperty("response_format")
    private ResponseFormat responseFormat = new ResponseFormat("json_object");

    /**
     * Constructs a new LlmRequest with the essential fields.
     * The response format is initialized to a default value of "json_object".
     *
     * @param model    The identifier of the model to use.
     * @param messages The list of messages forming the conversation.
     */
    public LlmRequest(String model, List<LlmMessage> messages) {
        this.model = model;
        this.messages = messages;
    }

    /**
     * A nested static class to define the desired response format from the LLM.
     * This allows for specifying constraints on the model's output, such as requiring valid JSON.
     */
    @Data
    public static class ResponseFormat {

        /**
         * The type of response format required. For example, "json_object" instructs
         * the model to output a syntactically correct JSON object.
         */
        private String type;

        /**
         * Constructs a new ResponseFormat with a specified type.
         *
         * @param type The desired response format type (e.g., "json_object").
         */
        public ResponseFormat(String type) {
            this.type = type;
        }
    }
}