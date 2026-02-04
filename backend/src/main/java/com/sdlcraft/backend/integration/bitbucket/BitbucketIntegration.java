package com.sdlcraft.backend.integration.bitbucket;

import com.sdlcraft.backend.integration.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Bitbucket integration for source control operations.
 * 
 * Supports:
 * - Creating/managing pull requests
 * - Code review automation
 * - Branch management
 * - Commit linking
 */
@Component
public class BitbucketIntegration implements Integration {

    private static final Logger logger = LoggerFactory.getLogger(BitbucketIntegration.class);
    
    @Value("${sdlcraft.integration.bitbucket.url:https://api.bitbucket.org/2.0}")
    private String bitbucketUrl;
    
    @Value("${sdlcraft.integration.bitbucket.workspace:}")
    private String workspace;
    
    @Value("${sdlcraft.integration.bitbucket.username:}")
    private String username;
    
    @Value("${sdlcraft.integration.bitbucket.app-password:}")
    private String appPassword;
    
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getId() {
        return "bitbucket";
    }

    @Override
    public String getName() {
        return "Atlassian Bitbucket";
    }

    @Override
    public boolean isConfigured() {
        return workspace != null && !workspace.isEmpty()
            && username != null && !username.isEmpty()
            && appPassword != null && !appPassword.isEmpty();
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
                bitbucketUrl + "/user",
                HttpMethod.GET,
                entity,
                String.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return IntegrationHealth.healthy(getId(), System.currentTimeMillis() - start);
            }
            return IntegrationHealth.unhealthy(getId(), "Unexpected response: " + response.getStatusCode());
        } catch (Exception e) {
            logger.error("Bitbucket health check failed", e);
            return IntegrationHealth.unhealthy(getId(), e.getMessage());
        }
    }

    @Override
    public String[] getSupportedActions() {
        return new String[]{
            "createPullRequest",
            "getPullRequest",
            "mergePullRequest",
            "declinePullRequest",
            "addPRComment",
            "listBranches",
            "getCommit",
            "listRepositories"
        };
    }
    
    /**
     * Create a new pull request.
     */
    public IntegrationResult createPullRequest(String repoSlug, String title, 
                                                String sourceBranch, String targetBranch,
                                                String description) {
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> body = Map.of(
                "title", title,
                "description", description,
                "source", Map.of("branch", Map.of("name", sourceBranch)),
                "destination", Map.of("branch", Map.of("name", targetBranch)),
                "close_source_branch", true
            );
            
            HttpHeaders headers = createHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                bitbucketUrl + "/repositories/" + workspace + "/" + repoSlug + "/pullrequests",
                HttpMethod.POST,
                entity,
                Map.class
            );
            
            Integer prId = (Integer) response.getBody().get("id");
            return IntegrationResult.success(getId(), "createPullRequest",
                "Created PR #" + prId + ": " + title,
                response.getBody(),
                System.currentTimeMillis() - start);
        } catch (Exception e) {
            logger.error("Failed to create pull request", e);
            return IntegrationResult.failure(getId(), "createPullRequest", e.getMessage());
        }
    }
    
    /**
     * Get pull request details.
     */
    public IntegrationResult getPullRequest(String repoSlug, int prId) {
        long start = System.currentTimeMillis();
        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                bitbucketUrl + "/repositories/" + workspace + "/" + repoSlug + "/pullrequests/" + prId,
                HttpMethod.GET,
                entity,
                Map.class
            );
            
            return IntegrationResult.success(getId(), "getPullRequest",
                "Retrieved PR #" + prId,
                response.getBody(),
                System.currentTimeMillis() - start);
        } catch (Exception e) {
            logger.error("Failed to get PR #{}", prId, e);
            return IntegrationResult.failure(getId(), "getPullRequest", e.getMessage());
        }
    }
    
    /**
     * Merge a pull request.
     */
    public IntegrationResult mergePullRequest(String repoSlug, int prId, String mergeStrategy) {
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> body = Map.of(
                "merge_strategy", mergeStrategy != null ? mergeStrategy : "merge_commit",
                "close_source_branch", true
            );
            
            HttpHeaders headers = createHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                bitbucketUrl + "/repositories/" + workspace + "/" + repoSlug + "/pullrequests/" + prId + "/merge",
                HttpMethod.POST,
                entity,
                Map.class
            );
            
            return IntegrationResult.success(getId(), "mergePullRequest",
                "Merged PR #" + prId,
                response.getBody(),
                System.currentTimeMillis() - start);
        } catch (Exception e) {
            logger.error("Failed to merge PR #{}", prId, e);
            return IntegrationResult.failure(getId(), "mergePullRequest", e.getMessage());
        }
    }
    
    /**
     * Add a comment to a pull request.
     */
    public IntegrationResult addPRComment(String repoSlug, int prId, String comment) {
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> body = Map.of(
                "content", Map.of("raw", comment)
            );
            
            HttpHeaders headers = createHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                bitbucketUrl + "/repositories/" + workspace + "/" + repoSlug + "/pullrequests/" + prId + "/comments",
                HttpMethod.POST,
                entity,
                Map.class
            );
            
            return IntegrationResult.success(getId(), "addPRComment",
                "Added comment to PR #" + prId,
                response.getBody(),
                System.currentTimeMillis() - start);
        } catch (Exception e) {
            logger.error("Failed to add comment to PR #{}", prId, e);
            return IntegrationResult.failure(getId(), "addPRComment", e.getMessage());
        }
    }
    
    /**
     * List branches in a repository.
     */
    public IntegrationResult listBranches(String repoSlug) {
        long start = System.currentTimeMillis();
        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                bitbucketUrl + "/repositories/" + workspace + "/" + repoSlug + "/refs/branches",
                HttpMethod.GET,
                entity,
                Map.class
            );
            
            List<Map> branches = (List<Map>) response.getBody().get("values");
            return IntegrationResult.success(getId(), "listBranches",
                "Found " + branches.size() + " branches",
                response.getBody(),
                System.currentTimeMillis() - start);
        } catch (Exception e) {
            logger.error("Failed to list branches for {}", repoSlug, e);
            return IntegrationResult.failure(getId(), "listBranches", e.getMessage());
        }
    }
    
    /**
     * Generate AI-powered PR description.
     */
    public String generatePRDescription(String commitMessages, String diffSummary) {
        // This would call the LLM to generate a description
        // For now return a template
        return String.format("""
            ## Summary
            This PR includes the following changes:
            
            ## Commits
            %s
            
            ## Changes
            %s
            
            ## Testing
            - [ ] Unit tests pass
            - [ ] Integration tests pass
            - [ ] Manual testing completed
            """, commitMessages, diffSummary);
    }
    
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String auth = username + ":" + appPassword;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        headers.set("Authorization", "Basic " + encodedAuth);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }
}

