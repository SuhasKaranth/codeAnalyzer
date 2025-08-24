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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("LLMAction{");
        sb.append("actionName='").append(actionName).append('\'');
        sb.append(", parameters={");
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            sb.append("\n  ").append(entry.getKey()).append("=").append(entry.getValue());
        }
        sb.append("\n}}");
        return sb.toString();
    }

}