package com.sdlcraft.backend.integration.jira;

import com.sdlcraft.backend.integration.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Jira integration for ticket management, issue tracking, and workflow automation.
 * 
 * Supports:
 * - Creating/updating issues
 * - Transitioning issue status
 * - Linking commits and PRs to issues
 * - Syncing project state with SDLC phases
 */
@Component
public class JiraIntegration implements Integration {

    private static final Logger logger = LoggerFactory.getLogger(JiraIntegration.class);
    
    @Value("${sdlcraft.integration.jira.url:}")
    private String jiraUrl;
    
    @Value("${sdlcraft.integration.jira.email:}")
    private String jiraEmail;
    
    @Value("${sdlcraft.integration.jira.token:}")
    private String jiraToken;
    
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getId() {
        return "jira";
    }

    @Override
    public String getName() {
        return "Atlassian Jira";
    }

    @Override
    public boolean isConfigured() {
        return jiraUrl != null && !jiraUrl.isEmpty() 
            && jiraToken != null && !jiraToken.isEmpty();
    }

    @Override
    public IntegrationHealth healthCheck() {
        if (!isConfigured()) {
            return IntegrationHealth.unhealthy(getId(), "Not configured");
        }
        
        long start = System.currentTimeMillis();
        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                jiraUrl + "/rest/api/3/myself",
                HttpMethod.GET,
                entity,
                String.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return IntegrationHealth.healthy(getId(), System.currentTimeMillis() - start);
            }
            return IntegrationHealth.unhealthy(getId(), "Unexpected response: " + response.getStatusCode());
        } catch (Exception e) {
            logger.error("Jira health check failed", e);
            return IntegrationHealth.unhealthy(getId(), e.getMessage());
        }
    }

    @Override
    public String[] getSupportedActions() {
        return new String[]{
            "getIssue",
            "createIssue", 
            "updateIssue",
            "transitionIssue",
            "addComment",
            "searchIssues",
            "linkToCommit",
            "getProjectIssues"
        };
    }
    
    /**
     * Get issue details by key (e.g., PROJ-123).
     */
    public IntegrationResult getIssue(String issueKey) {
        long start = System.currentTimeMillis();
        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                jiraUrl + "/rest/api/3/issue/" + issueKey,
                HttpMethod.GET,
                entity,
                Map.class
            );
            
            return IntegrationResult.success(getId(), "getIssue", 
                "Retrieved issue " + issueKey, 
                response.getBody(),
                System.currentTimeMillis() - start);
        } catch (Exception e) {
            logger.error("Failed to get issue {}", issueKey, e);
            return IntegrationResult.failure(getId(), "getIssue", e.getMessage());
        }
    }
    
    /**
     * Create a new Jira issue.
     */
    public IntegrationResult createIssue(String projectKey, String issueType, 
                                         String summary, String description) {
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> fields = new HashMap<>();
            fields.put("project", Map.of("key", projectKey));
            fields.put("issuetype", Map.of("name", issueType));
            fields.put("summary", summary);
            fields.put("description", Map.of(
                "type", "doc",
                "version", 1,
                "content", List.of(Map.of(
                    "type", "paragraph",
                    "content", List.of(Map.of("type", "text", "text", description))
                ))
            ));
            
            Map<String, Object> body = Map.of("fields", fields);
            
            HttpHeaders headers = createHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                jiraUrl + "/rest/api/3/issue",
                HttpMethod.POST,
                entity,
                Map.class
            );
            
            String createdKey = (String) response.getBody().get("key");
            return IntegrationResult.success(getId(), "createIssue",
                "Created issue " + createdKey,
                response.getBody(),
                System.currentTimeMillis() - start);
        } catch (Exception e) {
            logger.error("Failed to create issue", e);
            return IntegrationResult.failure(getId(), "createIssue", e.getMessage());
        }
    }
    
    /**
     * Transition an issue to a new status.
     */
    public IntegrationResult transitionIssue(String issueKey, String transitionName) {
        long start = System.currentTimeMillis();
        try {
            // First get available transitions
            HttpHeaders headers = createHeaders();
            HttpEntity<String> getEntity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> transitionsResponse = restTemplate.exchange(
                jiraUrl + "/rest/api/3/issue/" + issueKey + "/transitions",
                HttpMethod.GET,
                getEntity,
                Map.class
            );
            
            List<Map<String, Object>> transitions = 
                (List<Map<String, Object>>) transitionsResponse.getBody().get("transitions");
            
            Optional<Map<String, Object>> targetTransition = transitions.stream()
                .filter(t -> transitionName.equalsIgnoreCase((String) t.get("name")))
                .findFirst();
            
            if (targetTransition.isEmpty()) {
                return IntegrationResult.failure(getId(), "transitionIssue",
                    "Transition '" + transitionName + "' not available for " + issueKey);
            }
            
            // Execute transition
            Map<String, Object> body = Map.of(
                "transition", Map.of("id", targetTransition.get().get("id"))
            );
            
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> postEntity = new HttpEntity<>(body, headers);
            
            restTemplate.exchange(
                jiraUrl + "/rest/api/3/issue/" + issueKey + "/transitions",
                HttpMethod.POST,
                postEntity,
                Void.class
            );
            
            return IntegrationResult.success(getId(), "transitionIssue",
                "Transitioned " + issueKey + " to " + transitionName,
                Map.of("issueKey", issueKey, "newStatus", transitionName),
                System.currentTimeMillis() - start);
        } catch (Exception e) {
            logger.error("Failed to transition issue {}", issueKey, e);
            return IntegrationResult.failure(getId(), "transitionIssue", e.getMessage());
        }
    }
    
    /**
     * Search issues using JQL.
     */
    public IntegrationResult searchIssues(String jql, int maxResults) {
        long start = System.currentTimeMillis();
        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            String url = jiraUrl + "/rest/api/3/search?jql=" + 
                java.net.URLEncoder.encode(jql, "UTF-8") + "&maxResults=" + maxResults;
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                Map.class
            );
            
            List<Map> issues = (List<Map>) response.getBody().get("issues");
            return IntegrationResult.success(getId(), "searchIssues",
                "Found " + issues.size() + " issues",
                response.getBody(),
                System.currentTimeMillis() - start);
        } catch (Exception e) {
            logger.error("Failed to search issues with JQL: {}", jql, e);
            return IntegrationResult.failure(getId(), "searchIssues", e.getMessage());
        }
    }
    
    /**
     * Add a comment to an issue.
     */
    public IntegrationResult addComment(String issueKey, String comment) {
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> body = Map.of(
                "body", Map.of(
                    "type", "doc",
                    "version", 1,
                    "content", List.of(Map.of(
                        "type", "paragraph",
                        "content", List.of(Map.of("type", "text", "text", comment))
                    ))
                )
            );
            
            HttpHeaders headers = createHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            
            restTemplate.exchange(
                jiraUrl + "/rest/api/3/issue/" + issueKey + "/comment",
                HttpMethod.POST,
                entity,
                Map.class
            );
            
            return IntegrationResult.success(getId(), "addComment",
                "Added comment to " + issueKey,
                Map.of("issueKey", issueKey),
                System.currentTimeMillis() - start);
        } catch (Exception e) {
            logger.error("Failed to add comment to issue {}", issueKey, e);
            return IntegrationResult.failure(getId(), "addComment", e.getMessage());
        }
    }
    
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String auth = jiraEmail + ":" + jiraToken;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        headers.set("Authorization", "Basic " + encodedAuth);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }
}

