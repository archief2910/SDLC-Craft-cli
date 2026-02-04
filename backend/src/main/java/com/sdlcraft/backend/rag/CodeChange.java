package com.sdlcraft.backend.rag;

/**
 * Represents a code change suggested by the RAG system.
 */
public class CodeChange {
    
    private String filePath;
    private String action; // CREATE, MODIFY, DELETE
    private String description;
    private String diff;
    private String newContent;
    private String oldContent;
    private int startLine;
    private int endLine;
    
    public CodeChange() {}
    
    public CodeChange(String filePath, String action, String description) {
        this.filePath = filePath;
        this.action = action;
        this.description = description;
    }
    
    // Getters and setters
    
    public String getFilePath() {
        return filePath;
    }
    
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    
    public String getAction() {
        return action;
    }
    
    public void setAction(String action) {
        this.action = action;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getDiff() {
        return diff;
    }
    
    public void setDiff(String diff) {
        this.diff = diff;
    }
    
    public String getNewContent() {
        return newContent;
    }
    
    public void setNewContent(String newContent) {
        this.newContent = newContent;
    }
    
    public String getOldContent() {
        return oldContent;
    }
    
    public void setOldContent(String oldContent) {
        this.oldContent = oldContent;
    }
    
    public int getStartLine() {
        return startLine;
    }
    
    public void setStartLine(int startLine) {
        this.startLine = startLine;
    }
    
    public int getEndLine() {
        return endLine;
    }
    
    public void setEndLine(int endLine) {
        this.endLine = endLine;
    }
    
    public boolean isCreate() {
        return "CREATE".equalsIgnoreCase(action);
    }
    
    public boolean isModify() {
        return "MODIFY".equalsIgnoreCase(action);
    }
    
    public boolean isDelete() {
        return "DELETE".equalsIgnoreCase(action);
    }
    
    @Override
    public String toString() {
        return String.format("CodeChange{action=%s, file=%s, description=%s}", 
                action, filePath, description);
    }
}





