package com.suhas.codeAnalyzer.chat.model;

import java.time.Instant;

public class ChatExchange {
    private final String userMessage;
    private final String assistantResponse;
    private final Instant timestamp;

    public ChatExchange(String userMessage, String assistantResponse, Instant timestamp) {
        this.userMessage = userMessage;
        this.assistantResponse = assistantResponse;
        this.timestamp = timestamp;
    }

    // Getters
    public String getUserMessage() { return userMessage; }
    public String getAssistantResponse() { return assistantResponse; }
    public Instant getTimestamp() { return timestamp; }
}