package com.agent.dto.response;

public record CommandResponse(boolean success, String message) {

    // Helper method for colored output in the shell
    public String toAnsiString() {
        String color = success ? "\u001B[32m" : "\u001B[31m"; // Green for success, Red for failure
        return color + message + "\u001B[0m";
    }
}