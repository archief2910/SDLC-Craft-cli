package com.sdlcraft.backend.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * Response from RAG query containing explanation and suggested changes.
 */
public class RAGResponse {
    
    private String query;
    private String focusFile;
    private String explanation;
    private String rawResponse;
    private List<CodeChange> changes = new ArrayList<>();
    private List<String> commands = new ArrayList<>();
    private List<String> referencedFiles = new ArrayList<>();
    private int contextChunks;
    private boolean success = true;
    private String error;
    
    public RAGResponse() {}
    
    public static RAGResponse error(String message) {
        RAGResponse response = new RAGResponse();
        response.setSuccess(false);
        response.setError(message);
        return response;
    }
    
    // Getters and setters
    
    public String getQuery() {
        return query;
    }
    
    public void setQuery(String query) {
        this.query = query;
    }
    
    public String getFocusFile() {
        return focusFile;
    }
    
    public void setFocusFile(String focusFile) {
        this.focusFile = focusFile;
    }
    
    public String getExplanation() {
        return explanation;
    }
    
    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }
    
    public String getRawResponse() {
        return rawResponse;
    }
    
    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }
    
    public List<CodeChange> getChanges() {
        return changes;
    }
    
    public void setChanges(List<CodeChange> changes) {
        this.changes = changes;
    }
    
    public List<String> getCommands() {
        return commands;
    }
    
    public void setCommands(List<String> commands) {
        this.commands = commands;
    }
    
    public List<String> getReferencedFiles() {
        return referencedFiles;
    }
    
    public void setReferencedFiles(List<String> referencedFiles) {
        this.referencedFiles = referencedFiles;
    }
    
    public int getContextChunks() {
        return contextChunks;
    }
    
    public void setContextChunks(int contextChunks) {
        this.contextChunks = contextChunks;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getError() {
        return error;
    }
    
    public void setError(String error) {
        this.error = error;
    }
}




