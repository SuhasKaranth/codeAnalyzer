package com.suhas.codeAnalyzer.chat.model;

import java.util.HashMap;
import java.util.Map;

public class LLMAction {
    private final String actionName;
    private final Map<String, Object> parameters;

    public LLMAction(String actionName, Map<String, Object> parameters) {
        this.actionName = actionName;
        this.parameters = parameters != null ? new HashMap<>(parameters) : new HashMap<>();
    }

    // Getters
    public String getActionName() { return actionName; }
    public Map<String, Object> getParameters() { return new HashMap<>(parameters); }
}