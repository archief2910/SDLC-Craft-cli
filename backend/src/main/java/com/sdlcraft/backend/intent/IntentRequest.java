package com.sdlcraft.backend.intent;

import java.util.HashMap;
import java.util.Map;

/**
 * IntentRequest represents a request to infer intent from user input.
 * 
 * This class encapsulates all the information needed to understand what the user
 * wants to accomplish, including the raw command, project context, user information,
 * and any additional metadata.
 */
public class IntentRequest {
    
    private String rawCommand;
    private String userId;
    private String projectId;
    private String projectPath;
    private Map<String, Object> context;
    
    public IntentRequest() {
        this.context = new HashMap<>();
    }
    
    public IntentRequest(String rawCommand, String userId, String projectId) {
        this.rawCommand = rawCommand;
        this.userId = userId;
        this.projectId = projectId;
        this.context = new HashMap<>();
    }
    
    // Getters and setters
    
    public String getRawCommand() {
        return rawCommand;
    }
    
    public void setRawCommand(String rawCommand) {
        this.rawCommand = rawCommand;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getProjectId() {
        return projectId;
    }
    
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }
    
    public String getProjectPath() {
        return projectPath;
    }
    
    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath;
    }
    
    public Map<String, Object> getContext() {
        return context;
    }
    
    public void setContext(Map<String, Object> context) {
        this.context = context;
    }
    
    public void addContext(String key, Object value) {
        this.context.put(key, value);
    }
    
    @Override
    public String toString() {
        return "IntentRequest{" +
                "rawCommand='" + rawCommand + '\'' +
                ", userId='" + userId + '\'' +
                ", projectId='" + projectId + '\'' +
                ", projectPath='" + projectPath + '\'' +
                ", context=" + context +
                '}';
    }
}
