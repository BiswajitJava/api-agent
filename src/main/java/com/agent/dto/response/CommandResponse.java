package com.agent.dto.response;

/**
 * A record that represents the outcome of a shell command execution.
 * This is an immutable data carrier used to standardize command results,
 * providing a clear success status and a corresponding message.
 *
 * @param success A boolean flag indicating whether the command executed successfully.
 * @param message A descriptive message detailing the result of the command,
 *                such as a success confirmation or an error explanation.
 */
public record CommandResponse(boolean success, String message) {

    /**
     * Formats the response message with ANSI color codes for enhanced terminal output.
     * It colors the message green for success and red for failure, resetting the color
     * at the end of the string.
     *
     * @return A string containing the message wrapped in ANSI color codes.
     */
    public String toAnsiString() {
        String color = success ? "\u001B[32m" : "\u001B[31m"; // Green for success, Red for failure
        return color + message + "\u001B[0m"; // Reset color at the end
    }
}