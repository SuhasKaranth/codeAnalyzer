package com.suhas.codeAnalyzer.chat.model;

public class ActionResult {
    private final boolean success;
    private final String message;
    private final Object data;
    private final String errorDetails;

    private ActionResult(boolean success, String message, Object data, String errorDetails) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.errorDetails = errorDetails;
    }

    public static ActionResult success(String message, Object data) {
        return new ActionResult(true, message, data, null);
    }

    public static ActionResult error(String errorMessage) {
        return new ActionResult(false, null, null, errorMessage);
    }

    // Getters
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public Object getData() { return data; }
    public String getErrorDetails() { return errorDetails; }
}