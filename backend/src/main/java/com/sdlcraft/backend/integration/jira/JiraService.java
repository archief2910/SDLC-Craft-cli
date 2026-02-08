package com.sdlcraft.backend.integration.jira;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Jira Integration Service.
 * 
 * Provides integration with Jira for:
 * - Fetching project tickets
 * - Creating new issues
 * - Transitioning issue status
 * - Adding comments
 * - Linking code changes to issues
 */
@Service
public class JiraService {
    
    private static final Logger logger = LoggerFactory.getLogger(JiraService.class);
    
    private final String baseUrl;
    private final String email;
    private final String apiToken;
    private final RestTemplate restTemplate;
    private final boolean available;
    
    public JiraService(
            @Value("${sdlcraft.integrations.jira.url:}") String baseUrl,
            @Value("${sdlcraft.integrations.jira.email:}") String email,
            @Value("${sdlcraft.integrations.jira.token:}") String apiToken) {
        
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/$", "") : "";
        this.email = email != null ? email : "";
        this.apiToken = apiToken != null ? apiToken : "";
        
        this.restTemplate = new RestTemplate();
        this.restTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
            setConnectTimeout(10000);
            setReadTimeout(30000);
        }});
        
        this.available = !this.baseUrl.isEmpty() && !this.email.isEmpty() && !this.apiToken.isEmpty();
        
        if (available) {
            logger.info("✅ Jira integration initialized: {}", this.baseUrl);
        } else {
            logger.warn("⚠️ Jira integration not configured. Set JIRA_URL, JIRA_EMAIL, JIRA_TOKEN");
        }
    }
    
    /**
     * Check if Jira integration is available.
     */
    public boolean isAvailable() {
        return available;
    }
    
    /**
     * Get all projects.
     */
    public List<JiraProject> getProjects() {
        if (!available) {
            throw new JiraException("Jira not configured");
        }
        
        try {
            String url = baseUrl + "/rest/api/3/project";
            ResponseEntity<List> response = restTemplate.exchange(
                    url, HttpMethod.GET, createAuthEntity(null), List.class);
            
            List<Map<String, Object>> projects = response.getBody();
            if (projects == null) return Collections.emptyList();
            
            return projects.stream()
                    .map(p -> new JiraProject(
                            (String) p.get("id"),
                            (String) p.get("key"),
                            (String) p.get("name")
                    ))
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            logger.error("Failed to fetch Jira projects", e);
            throw new JiraException("Failed to fetch projects: " + e.getMessage());
        }
    }
    
    /**
     * Get issues for a project with optional JQL filter.
     */
    public List<JiraIssue> getIssues(String projectKey, String status, int maxResults) {
        if (!available) {
            throw new JiraException("Jira not configured");
        }
        
        try {
            StringBuilder jql = new StringBuilder("project = " + projectKey);
            if (status != null && !status.isEmpty()) {
                jql.append(" AND status = \"").append(status).append("\"");
            }
            jql.append(" ORDER BY updated DESC");
            
            // Use the new /search/jql POST endpoint (Jira API v3 2024+)
            String url = baseUrl + "/rest/api/3/search/jql";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("jql", jql.toString());
            requestBody.put("maxResults", maxResults);
            requestBody.put("fields", List.of("summary", "status", "assignee", "priority", "created", "updated", "description"));
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, createAuthEntity(requestBody), Map.class);
            
            Map<String, Object> body = response.getBody();
            if (body == null) return Collections.emptyList();
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> issues = (List<Map<String, Object>>) body.get("issues");
            if (issues == null) return Collections.emptyList();
            
            return issues.stream()
                    .map(this::parseIssue)
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            logger.error("Failed to fetch Jira issues", e);
            throw new JiraException("Failed to fetch issues: " + e.getMessage());
        }
    }
    
    /**
     * Get a single issue by key.
     */
    public JiraIssue getIssue(String issueKey) {
        if (!available) {
            throw new JiraException("Jira not configured");
        }
        
        try {
            String url = baseUrl + "/rest/api/3/issue/" + issueKey;
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, createAuthEntity(null), Map.class);
            
            return parseIssue(response.getBody());
            
        } catch (Exception e) {
            logger.error("Failed to fetch Jira issue: {}", issueKey, e);
            throw new JiraException("Failed to fetch issue: " + e.getMessage());
        }
    }
    
    /**
     * Create a new issue.
     */
    public JiraIssue createIssue(String projectKey, String issueType, String summary, String description) {
        if (!available) {
            throw new JiraException("Jira not configured");
        }
        
        try {
            String url = baseUrl + "/rest/api/3/issue";
            
            Map<String, Object> fields = new HashMap<>();
            fields.put("project", Map.of("key", projectKey));
            fields.put("issuetype", Map.of("name", issueType));
            fields.put("summary", summary);
            
            if (description != null && !description.isEmpty()) {
                // Atlassian Document Format (ADF) for description
                fields.put("description", Map.of(
                        "type", "doc",
                        "version", 1,
                        "content", List.of(Map.of(
                                "type", "paragraph",
                                "content", List.of(Map.of(
                                        "type", "text",
                                        "text", description
                                ))
                        ))
                ));
            }
            
            Map<String, Object> request = Map.of("fields", fields);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, createAuthEntity(request), Map.class);
            
            Map<String, Object> body = response.getBody();
            String key = (String) body.get("key");
            
            logger.info("Created Jira issue: {}", key);
            return getIssue(key);
            
        } catch (Exception e) {
            logger.error("Failed to create Jira issue", e);
            throw new JiraException("Failed to create issue: " + e.getMessage());
        }
    }
    
    /**
     * Transition an issue to a new status.
     */
    public void transitionIssue(String issueKey, String targetStatus) {
        if (!available) {
            throw new JiraException("Jira not configured");
        }
        
        try {
            // First, get available transitions
            String transitionsUrl = baseUrl + "/rest/api/3/issue/" + issueKey + "/transitions";
            ResponseEntity<Map> transitionsResponse = restTemplate.exchange(
                    transitionsUrl, HttpMethod.GET, createAuthEntity(null), Map.class);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> transitions = 
                    (List<Map<String, Object>>) transitionsResponse.getBody().get("transitions");
            
            // Find the transition that matches the target status
            String transitionId = null;
            for (Map<String, Object> transition : transitions) {
                @SuppressWarnings("unchecked")
                Map<String, Object> to = (Map<String, Object>) transition.get("to");
                String statusName = (String) to.get("name");
                if (statusName.equalsIgnoreCase(targetStatus)) {
                    transitionId = (String) transition.get("id");
                    break;
                }
            }
            
            if (transitionId == null) {
                throw new JiraException("Cannot transition to status: " + targetStatus + 
                        ". Available: " + transitions.stream()
                        .map(t -> ((Map<String, Object>) t.get("to")).get("name"))
                        .collect(Collectors.toList()));
            }
            
            // Perform the transition
            Map<String, Object> request = Map.of(
                    "transition", Map.of("id", transitionId)
            );
            
            restTemplate.exchange(transitionsUrl, HttpMethod.POST, createAuthEntity(request), Void.class);
            
            logger.info("Transitioned {} to {}", issueKey, targetStatus);
            
        } catch (JiraException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to transition Jira issue: {}", issueKey, e);
            throw new JiraException("Failed to transition issue: " + e.getMessage());
        }
    }
    
    /**
     * Add a comment to an issue.
     */
    public void addComment(String issueKey, String comment) {
        if (!available) {
            throw new JiraException("Jira not configured");
        }
        
        try {
            String url = baseUrl + "/rest/api/3/issue/" + issueKey + "/comment";
            
            Map<String, Object> request = Map.of(
                    "body", Map.of(
                            "type", "doc",
                            "version", 1,
                            "content", List.of(Map.of(
                                    "type", "paragraph",
                                    "content", List.of(Map.of(
                                            "type", "text",
                                            "text", comment
                                    ))
                            ))
                    )
            );
            
            restTemplate.exchange(url, HttpMethod.POST, createAuthEntity(request), Map.class);
            
            logger.info("Added comment to {}", issueKey);
            
        } catch (Exception e) {
            logger.error("Failed to add comment to Jira issue: {}", issueKey, e);
            throw new JiraException("Failed to add comment: " + e.getMessage());
        }
    }
    
    /**
     * Create HTTP entity with authentication.
     */
    private HttpEntity<Object> createAuthEntity(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        
        // Basic auth with email:token
        String auth = email + ":" + apiToken;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        headers.set("Authorization", "Basic " + encodedAuth);
        
        return new HttpEntity<>(body, headers);
    }
    
    /**
     * Parse issue response into JiraIssue object.
     */
    @SuppressWarnings("unchecked")
    private JiraIssue parseIssue(Map<String, Object> issueData) {
        String key = (String) issueData.get("key");
        Map<String, Object> fields = (Map<String, Object>) issueData.get("fields");
        
        String summary = (String) fields.get("summary");
        
        Map<String, Object> statusObj = (Map<String, Object>) fields.get("status");
        String status = statusObj != null ? (String) statusObj.get("name") : "Unknown";
        
        Map<String, Object> priorityObj = (Map<String, Object>) fields.get("priority");
        String priority = priorityObj != null ? (String) priorityObj.get("name") : "None";
        
        Map<String, Object> assigneeObj = (Map<String, Object>) fields.get("assignee");
        String assignee = assigneeObj != null ? (String) assigneeObj.get("displayName") : "Unassigned";
        
        String created = (String) fields.get("created");
        String updated = (String) fields.get("updated");
        
        // Parse description from ADF format
        String description = "";
        Object descObj = fields.get("description");
        if (descObj instanceof Map) {
            Map<String, Object> descDoc = (Map<String, Object>) descObj;
            List<Map<String, Object>> content = (List<Map<String, Object>>) descDoc.get("content");
            if (content != null && !content.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Map<String, Object> block : content) {
                    List<Map<String, Object>> blockContent = (List<Map<String, Object>>) block.get("content");
                    if (blockContent != null) {
                        for (Map<String, Object> textBlock : blockContent) {
                            String text = (String) textBlock.get("text");
                            if (text != null) sb.append(text);
                        }
                    }
                    sb.append("\n");
                }
                description = sb.toString().trim();
            }
        }
        
        return new JiraIssue(key, summary, status, priority, assignee, description, created, updated);
    }
    
    // DTO Classes
    
    public static class JiraProject {
        private final String id;
        private final String key;
        private final String name;
        
        public JiraProject(String id, String key, String name) {
            this.id = id;
            this.key = key;
            this.name = name;
        }
        
        public String getId() { return id; }
        public String getKey() { return key; }
        public String getName() { return name; }
    }
    
    public static class JiraIssue {
        private final String key;
        private final String summary;
        private final String status;
        private final String priority;
        private final String assignee;
        private final String description;
        private final String created;
        private final String updated;
        
        public JiraIssue(String key, String summary, String status, String priority,
                        String assignee, String description, String created, String updated) {
            this.key = key;
            this.summary = summary;
            this.status = status;
            this.priority = priority;
            this.assignee = assignee;
            this.description = description;
            this.created = created;
            this.updated = updated;
        }
        
        public String getKey() { return key; }
        public String getSummary() { return summary; }
        public String getStatus() { return status; }
        public String getPriority() { return priority; }
        public String getAssignee() { return assignee; }
        public String getDescription() { return description; }
        public String getCreated() { return created; }
        public String getUpdated() { return updated; }
    }
}

