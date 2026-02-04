package com.sdlcraft.backend.controller;

import com.sdlcraft.backend.integration.*;
import com.sdlcraft.backend.integration.jira.JiraIntegration;
import com.sdlcraft.backend.integration.bitbucket.BitbucketIntegration;
import com.sdlcraft.backend.integration.aws.AWSIntegration;
import com.sdlcraft.backend.integration.docker.DockerIntegration;
import com.sdlcraft.backend.integration.qa.QAIntegration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for direct integration actions.
 */
@RestController
@RequestMapping("/api/integration")
public class IntegrationController {

    private final JiraIntegration jiraIntegration;
    private final BitbucketIntegration bitbucketIntegration;
    private final AWSIntegration awsIntegration;
    private final DockerIntegration dockerIntegration;
    private final QAIntegration qaIntegration;

    public IntegrationController(
        JiraIntegration jiraIntegration,
        BitbucketIntegration bitbucketIntegration,
        AWSIntegration awsIntegration,
        DockerIntegration dockerIntegration,
        QAIntegration qaIntegration
    ) {
        this.jiraIntegration = jiraIntegration;
        this.bitbucketIntegration = bitbucketIntegration;
        this.awsIntegration = awsIntegration;
        this.dockerIntegration = dockerIntegration;
        this.qaIntegration = qaIntegration;
    }

    @PostMapping("/jira/execute")
    public ResponseEntity<IntegrationResult> executeJira(@RequestBody Map<String, Object> request) {
        String action = (String) request.get("action");
        
        IntegrationResult result = switch (action) {
            case "getIssue" -> jiraIntegration.getIssue((String) request.get("issueKey"));
            case "createIssue" -> jiraIntegration.createIssue(
                (String) request.get("projectKey"),
                (String) request.get("issueType"),
                (String) request.get("summary"),
                (String) request.get("description")
            );
            case "transitionIssue" -> jiraIntegration.transitionIssue(
                (String) request.get("issueKey"),
                (String) request.get("transitionName")
            );
            case "addComment" -> jiraIntegration.addComment(
                (String) request.get("issueKey"),
                (String) request.get("comment")
            );
            case "searchIssues" -> jiraIntegration.searchIssues(
                (String) request.get("jql"),
                request.get("maxResults") != null ? (Integer) request.get("maxResults") : 20
            );
            default -> IntegrationResult.failure("jira", action, "Unknown action: " + action);
        };
        
        return ResponseEntity.ok(result);
    }

    @PostMapping("/bitbucket/execute")
    public ResponseEntity<IntegrationResult> executeBitbucket(@RequestBody Map<String, Object> request) {
        String action = (String) request.get("action");
        
        IntegrationResult result = switch (action) {
            case "createPullRequest" -> bitbucketIntegration.createPullRequest(
                (String) request.get("repoSlug"),
                (String) request.get("title"),
                (String) request.get("sourceBranch"),
                (String) request.get("targetBranch"),
                (String) request.get("description")
            );
            case "getPullRequest" -> bitbucketIntegration.getPullRequest(
                (String) request.get("repoSlug"),
                (Integer) request.get("prId")
            );
            case "mergePullRequest" -> bitbucketIntegration.mergePullRequest(
                (String) request.get("repoSlug"),
                (Integer) request.get("prId"),
                (String) request.get("mergeStrategy")
            );
            case "addPRComment" -> bitbucketIntegration.addPRComment(
                (String) request.get("repoSlug"),
                (Integer) request.get("prId"),
                (String) request.get("comment")
            );
            case "listBranches" -> bitbucketIntegration.listBranches(
                (String) request.get("repoSlug")
            );
            default -> IntegrationResult.failure("bitbucket", action, "Unknown action: " + action);
        };
        
        return ResponseEntity.ok(result);
    }

    @PostMapping("/aws/execute")
    public ResponseEntity<IntegrationResult> executeAws(@RequestBody Map<String, Object> request) {
        String action = (String) request.get("action");
        
        IntegrationResult result = switch (action) {
            case "deployEcsService" -> awsIntegration.deployEcsService(
                (String) request.get("cluster"),
                (String) request.get("service"),
                (String) request.get("taskDefinition"),
                request.get("desiredCount") != null ? (Integer) request.get("desiredCount") : 1
            );
            case "describeEcsService" -> awsIntegration.describeEcsService(
                (String) request.get("cluster"),
                (String) request.get("service")
            );
            case "describeEcrImages" -> awsIntegration.describeEcrImages(
                (String) request.get("repositoryName")
            );
            case "getCallerIdentity" -> awsIntegration.getCallerIdentity();
            default -> IntegrationResult.failure("aws", action, "Unknown action: " + action);
        };
        
        return ResponseEntity.ok(result);
    }

    @PostMapping("/docker/execute")
    @SuppressWarnings("unchecked")
    public ResponseEntity<IntegrationResult> executeDocker(@RequestBody Map<String, Object> request) {
        String action = (String) request.get("action");
        
        IntegrationResult result = switch (action) {
            case "build" -> dockerIntegration.build(
                (String) request.get("contextPath"),
                (String) request.get("imageName"),
                (String) request.get("tag"),
                (String) request.get("dockerfile"),
                (Map<String, String>) request.get("buildArgs")
            );
            case "push" -> dockerIntegration.push(
                (String) request.get("imageName"),
                (String) request.get("tag")
            );
            case "run" -> dockerIntegration.run(
                (String) request.get("imageName"),
                (String) request.get("containerName"),
                (Map<String, String>) request.get("envVars"),
                (Map<Integer, Integer>) request.get("portMappings"),
                request.get("detached") != null ? (Boolean) request.get("detached") : true
            );
            case "composeUp" -> dockerIntegration.composeUp(
                (String) request.get("composePath"),
                request.get("detached") != null ? (Boolean) request.get("detached") : true
            );
            case "composeDown" -> dockerIntegration.composeDown(
                (String) request.get("composePath")
            );
            case "listContainers" -> dockerIntegration.listContainers(
                request.get("all") != null ? (Boolean) request.get("all") : false
            );
            default -> IntegrationResult.failure("docker", action, "Unknown action: " + action);
        };
        
        return ResponseEntity.ok(result);
    }

    @PostMapping("/qa/execute")
    public ResponseEntity<IntegrationResult> executeQa(@RequestBody Map<String, Object> request) {
        String action = (String) request.get("action");
        
        IntegrationResult result = switch (action) {
            case "runTests" -> qaIntegration.runTests(
                (String) request.get("workingDir"),
                (String) request.get("testCommand")
            );
            case "runTestFile" -> qaIntegration.runTestFile(
                (String) request.get("workingDir"),
                (String) request.get("testFile")
            );
            case "generateTests" -> qaIntegration.generateTests(
                (String) request.get("codeFile"),
                (String) request.get("testFile")
            );
            case "iterativeTestFix" -> qaIntegration.iterativeTestFix(
                (String) request.get("codeFile"),
                (String) request.get("testFile"),
                (String) request.get("workingDir"),
                (String) request.get("testCommand")
            );
            case "analyzeCoverage" -> qaIntegration.analyzeCoverage(
                (String) request.get("workingDir")
            );
            default -> IntegrationResult.failure("qa", action, "Unknown action: " + action);
        };
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, IntegrationHealth>> getAllHealth() {
        return ResponseEntity.ok(Map.of(
            "jira", jiraIntegration.healthCheck(),
            "bitbucket", bitbucketIntegration.healthCheck(),
            "aws", awsIntegration.healthCheck(),
            "docker", dockerIntegration.healthCheck(),
            "qa", qaIntegration.healthCheck()
        ));
    }
}

