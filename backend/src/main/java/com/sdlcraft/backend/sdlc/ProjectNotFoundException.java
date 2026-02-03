package com.sdlcraft.backend.sdlc;

/**
 * Exception thrown when a project is not found in the SDLC state machine.
 */
public class ProjectNotFoundException extends RuntimeException {
    
    private final String projectId;
    
    public ProjectNotFoundException(String projectId) {
        super("Project not found: " + projectId);
        this.projectId = projectId;
    }
    
    public String getProjectId() {
        return projectId;
    }
}
