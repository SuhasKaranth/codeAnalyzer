package com.suhas.codeAnalyzer.chat.service;

import com.suhas.codeAnalyzer.chat.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatAgentService {

    private static final Logger log = LoggerFactory.getLogger(ChatAgentService.class);

    @Autowired
    private ChatLLMService chatLLMService;

    @Autowired
    private ChatMethodExecutor chatMethodExecutor;

    // In-memory session storage (use Redis in production)
    private final Map<String, ConversationContext> sessions = new ConcurrentHashMap<>();

    public ChatResponse processMessage(String userMessage, String sessionId) {
        try {
            log.info("Processing message for session: {}", sessionId);

            // Get or create conversation context
            ConversationContext context = getOrCreateContext(sessionId);

            // Call LLM with comprehensive prompt
            LLMResponse llmResponse = chatLLMService.callWithContext(userMessage, context);

            String finalResponse;
            ActionResult actionResult = null;

            // Execute action if required
            if (llmResponse.requiresAction()) {
                log.info("Executing action: {}", llmResponse.getLLMAction().getActionName());

                // Enhance parameters with context
                LLMAction enhancedAction = enhanceActionWithContext(llmResponse.getLLMAction(), context);

                actionResult = chatMethodExecutor.execute(enhancedAction);
                log.info("Inside ChatAgentService, post chatMethodExecutor.execute. Action result: {}", actionResult.toString());

                if (actionResult.isSuccess()) {
                    finalResponse = llmResponse.getResponse() + "\n\n" + actionResult.getMessage();

                    // Update context with successful action results
                    updateContextFromAction(context, enhancedAction, actionResult);
                } else {
                    finalResponse = "I encountered an error: " + actionResult.getMessage()+
                            ". Please try again or ask me something else.";
                }
            } else {
                finalResponse = llmResponse.getResponse();
            }

            // Add to conversation history
            context.addExchange(userMessage, finalResponse);

            // Build response
            ChatResponse response = ChatResponse.success(finalResponse, llmResponse.getSuggestions(), sessionId);

            // Add metadata
            if (actionResult != null) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("actionExecuted", llmResponse.getLLMAction().getActionName());
                metadata.put("actionSuccess", actionResult.isSuccess());

                if (actionResult.isSuccess() && actionResult.getData() != null) {
                    metadata.put("dataAvailable", true);
                }

                response.setMetadata(metadata);
            }

            return response;

        } catch (Exception e) {
            log.error("Error processing chat message", e);
            return ChatResponse.error("I'm having trouble processing your request. Please try again.", sessionId);
        }
    }

    private LLMAction enhanceActionWithContext(LLMAction action, ConversationContext context) {
        Map<String, Object> enhancedParams = new HashMap<>(action.getParameters());

        // Add current repository URL to actions that need it
        if (needsRepositoryUrl(action.getActionName()) && context.getCurrentRepository() != null) {
            enhancedParams.put("currentRepositoryUrl", context.getCurrentRepository());
        }

        return new LLMAction(action.getActionName(), enhancedParams);
    }

    private boolean needsRepositoryUrl(String actionName) {
        List<String> actionsNeedingUrl = new ArrayList<>();
        actionsNeedingUrl.add("LIST_FILES");
        return actionsNeedingUrl.contains(actionName);
    }

    public ConversationContext getContext(String sessionId) {
        return sessions.get(sessionId);
    }

    private ConversationContext getOrCreateContext(String sessionId) {
        return sessions.computeIfAbsent(sessionId, ConversationContext::new);
    }

    private void updateContextFromAction(ConversationContext context, LLMAction action, ActionResult result) {
        switch (action.getActionName()) {
            case "ANALYZE_REPO":
                String repoUrl = (String) action.getParameters().get("repositoryUrl");
                context.setCurrentRepository(repoUrl);

                // Extract repository ID from the result or generate one
                String repoId = extractRepositoryIdFromResult(result, repoUrl);
                context.setCurrentRepositoryId(repoId);

                log.info("Updated context with repository: {} (ID: {})", repoUrl, repoId);
                break;
        }
    }

    @SuppressWarnings("unchecked")
    private String extractRepositoryIdFromResult(ActionResult result, String repoUrl) {
        try {
            if (result.getData() instanceof Map) {
                Map<String, Object> data = (Map<String, Object>) result.getData();
                if (data.containsKey("repositoryId")) {
                    return (String) data.get("repositoryId");
                }
            }
        } catch (Exception e) {
            log.warn("Could not extract repository ID from result", e);
        }

        // Fallback: generate ID from URL
        return generateRepositoryId(repoUrl);
    }

    private String generateRepositoryId(String repoUrl) {
        if (repoUrl == null) return "unknown_repo";

        try {
            // Extract owner/repo from GitHub URL
            String[] parts = repoUrl.replace("https://github.com/", "")
                    .replace("http://github.com/", "")
                    .split("/");
            if (parts.length >= 2) {
                return parts[0] + "_" + parts[1];
            }
        } catch (Exception e) {
            log.warn("Failed to generate repository ID from URL: {}", repoUrl);
        }

        // Ultimate fallback
        return "repo_" + Math.abs(repoUrl.hashCode());
    }

    // Clean up old sessions (call this periodically)
    public void cleanupOldSessions() {
        long cutoff = System.currentTimeMillis() - (60 * 60 * 1000); // 1 hour
        sessions.entrySet().removeIf(entry ->
                entry.getValue().getLastActivity().toEpochMilli() < cutoff
        );
    }
}