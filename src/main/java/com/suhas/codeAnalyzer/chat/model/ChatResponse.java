package com.suhas.codeAnalyzer.chat.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatResponse {
    private String response;
    private List<String> suggestions;
    private boolean success;
    private String sessionId;
    private Map<String, Object> metadata;

    // Default constructor
    public ChatResponse() {
        this.suggestions = new ArrayList<>();
        this.metadata = new HashMap<>();
    }

    public ChatResponse(String response, List<String> suggestions, boolean success, String sessionId) {
        this.response = response;
        this.suggestions = suggestions != null ? suggestions : new ArrayList<>();
        this.success = success;
        this.sessionId = sessionId;
        this.metadata = new HashMap<>();
    }

    // Static factory methods
    public static ChatResponse success(String response, List<String> suggestions, String sessionId) {
        List<String> safeSuggestions = suggestions != null ? suggestions : new ArrayList<>();
        return new ChatResponse(response, safeSuggestions, true, sessionId);
    }

    public static ChatResponse error(String errorMessage, String sessionId) {
        List<String> defaultSuggestions = new ArrayList<>();
        defaultSuggestions.add("Try asking me something else");
        defaultSuggestions.add("What can you help me with?");
        return new ChatResponse(errorMessage, defaultSuggestions, false, sessionId);
    }

    // Getters and Setters
    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }
    public List<String> getSuggestions() { return suggestions; }
    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions != null ? suggestions : new ArrayList<>();
    }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }
}