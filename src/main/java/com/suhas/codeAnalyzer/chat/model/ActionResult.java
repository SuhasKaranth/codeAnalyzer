package com.suhas.codeAnalyzer.chat.model;

public class ActionResult {
    private boolean success;
    private String message;
    private Object data;

    private ActionResult(boolean success, String message, Object data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public static ActionResult success(String message) {
        return new ActionResult(true, message, null);
    }

    public static ActionResult success(String message, Object data) {
        return new ActionResult(true, message, data);
    }

    public static ActionResult error(String message) {
        return new ActionResult(false, message, null);
    }

    // Getters
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public Object getData() { return data; }

    @Override
    public String toString() {
        return "ActionResult{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", data=" + data +
                '}';
    }

}