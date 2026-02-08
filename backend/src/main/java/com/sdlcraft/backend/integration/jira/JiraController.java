package com.sdlcraft.backend.integration.jira;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for Jira integration endpoints.
 */
@RestController
@RequestMapping("/api/jira")
public class JiraController {
    
    private final JiraService jiraService;
    
    public JiraController(JiraService jiraService) {
        this.jiraService = jiraService;
    }
    
    /**
     * Check if Jira is configured and available.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "available", jiraService.isAvailable(),
                "message", jiraService.isAvailable() 
                        ? "Jira integration is configured" 
                        : "Jira not configured. Set JIRA_URL, JIRA_EMAIL, JIRA_TOKEN"
        ));
    }
    
    /**
     * Get all projects.
     */
    @GetMapping("/projects")
    public ResponseEntity<List<JiraService.JiraProject>> getProjects() {
        if (!jiraService.isAvailable()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(jiraService.getProjects());
    }
    
    /**
     * Get issues for a project.
     */
    @GetMapping("/issues")
    public ResponseEntity<List<JiraService.JiraIssue>> getIssues(
            @RequestParam String project,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit) {
        
        if (!jiraService.isAvailable()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(jiraService.getIssues(project, status, limit));
    }
    
    /**
     * Get a single issue.
     */
    @GetMapping("/issues/{key}")
    public ResponseEntity<JiraService.JiraIssue> getIssue(@PathVariable String key) {
        if (!jiraService.isAvailable()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(jiraService.getIssue(key));
    }
    
    /**
     * Create a new issue.
     */
    @PostMapping("/issues")
    public ResponseEntity<JiraService.JiraIssue> createIssue(@RequestBody CreateIssueRequest request) {
        if (!jiraService.isAvailable()) {
            return ResponseEntity.badRequest().build();
        }
        
        JiraService.JiraIssue issue = jiraService.createIssue(
                request.project(),
                request.issueType() != null ? request.issueType() : "Task",
                request.summary(),
                request.description()
        );
        
        return ResponseEntity.ok(issue);
    }
    
    /**
     * Transition an issue to a new status.
     */
    @PostMapping("/issues/{key}/transition")
    public ResponseEntity<Map<String, String>> transitionIssue(
            @PathVariable String key,
            @RequestBody TransitionRequest request) {
        
        if (!jiraService.isAvailable()) {
            return ResponseEntity.badRequest().build();
        }
        
        jiraService.transitionIssue(key, request.status());
        return ResponseEntity.ok(Map.of(
                "message", "Issue " + key + " transitioned to " + request.status()
        ));
    }
    
    /**
     * Add a comment to an issue.
     */
    @PostMapping("/issues/{key}/comment")
    public ResponseEntity<Map<String, String>> addComment(
            @PathVariable String key,
            @RequestBody CommentRequest request) {
        
        if (!jiraService.isAvailable()) {
            return ResponseEntity.badRequest().build();
        }
        
        jiraService.addComment(key, request.comment());
        return ResponseEntity.ok(Map.of(
                "message", "Comment added to " + key
        ));
    }
    
    // Request DTOs
    
    public record CreateIssueRequest(
            String project,
            String issueType,
            String summary,
            String description
    ) {}
    
    public record TransitionRequest(String status) {}
    
    public record CommentRequest(String comment) {}
}

