package com.suhas.codeAnalyzer.chat.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LLMResponse {
    private String action;
    private Map<String, Object> parameters;
    private String response;
    private List<String> suggestions;
    private boolean needsUserInput;

    // Default constructor
    public LLMResponse() {
        this.parameters = new HashMap<>();
        this.suggestions = new ArrayList<>();
    }

    public boolean requiresAction() {
        return action != null && !action.trim().isEmpty() && !"null".equals(action);
    }

    public LLMAction getLLMAction() {  // Changed method name to avoid conflict
        return new LLMAction(action, parameters);
    }

    // Getters and Setters
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters != null ? parameters : new HashMap<>();
    }
    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }
    public List<String> getSuggestions() { return suggestions; }
    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions != null ? suggestions : new ArrayList<>();
    }
    public boolean isNeedsUserInput() { return needsUserInput; }
    public void setNeedsUserInput(boolean needsUserInput) { this.needsUserInput = needsUserInput; }
}