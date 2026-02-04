package com.sdlcraft.backend.workflow;

import org.springframework.stereotype.Component;
import java.util.*;

/**
 * Predefined SDLC workflows combining multiple integrations.
 */
@Component
public class PredefinedWorkflows {

    /**
     * Bug fix workflow: Jira → Code → Test → PR → Deploy
     */
    public Workflow bugFixWorkflow() {
        return Workflow.builder()
            .id("bug-fix")
            .name("Bug Fix Pipeline")
            .description("Complete bug fix workflow from Jira ticket to deployment")
            .steps(List.of(
                WorkflowStep.builder()
                    .id("fetch-ticket")
                    .name("Fetch Jira Ticket")
                    .integrationId("jira")
                    .action("getIssue")
                    .parameters(Map.of("issueKey", "${jiraTicket}"))
                    .build(),
                    
                WorkflowStep.builder()
                    .id("transition-progress")
                    .name("Move to In Progress")
                    .integrationId("jira")
                    .action("transitionIssue")
                    .parameters(Map.of("issueKey", "${jiraTicket}", "transitionName", "In Progress"))
                    .continueOnFailure(true)
                    .build(),
                    
                WorkflowStep.builder()
                    .id("run-tests")
                    .name("Run Tests")
                    .integrationId("qa")
                    .action("runTests")
                    .parameters(Map.of("workingDir", "${workingDir}"))
                    .maxRetries(2)
                    .build(),
                    
                WorkflowStep.builder()
                    .id("create-pr")
                    .name("Create Pull Request")
                    .integrationId("bitbucket")
                    .action("createPullRequest")
                    .parameters(Map.of(
                        "repoSlug", "${repo}",
                        "title", "Fix: ${jiraTicket}",
                        "sourceBranch", "fix/${jiraTicket}",
                        "targetBranch", "main",
                        "description", "Fixes ${jiraTicket}"
                    ))
                    .condition("${lastStepSuccess}")
                    .build(),
                    
                WorkflowStep.builder()
                    .id("link-jira")
                    .name("Link PR to Jira")
                    .integrationId("jira")
                    .action("addComment")
                    .parameters(Map.of(
                        "issueKey", "${jiraTicket}",
                        "comment", "PR created: ${create-pr_output.links.html.href}"
                    ))
                    .continueOnFailure(true)
                    .build()
            ))
            .build();
    }
    
    /**
     * Feature development workflow with TDD.
     */
    public Workflow featureWorkflow() {
        return Workflow.builder()
            .id("feature-tdd")
            .name("Feature Development (TDD)")
            .description("Test-driven feature development with iterative AI fixes")
            .steps(List.of(
                WorkflowStep.builder()
                    .id("generate-tests")
                    .name("Generate Tests from Spec")
                    .integrationId("qa")
                    .action("generateTests")
                    .parameters(Map.of(
                        "codeFile", "${sourceFile}",
                        "testFile", "${testFile}"
                    ))
                    .build(),
                    
                WorkflowStep.builder()
                    .id("iterative-fix")
                    .name("Iterative Code Fix (micro-agent)")
                    .integrationId("qa")
                    .action("iterativeTestFix")
                    .parameters(Map.of(
                        "codeFile", "${sourceFile}",
                        "testFile", "${testFile}",
                        "workingDir", "${workingDir}"
                    ))
                    .maxRetries(0) // Has internal retry logic
                    .build(),
                    
                WorkflowStep.builder()
                    .id("coverage-check")
                    .name("Check Coverage")
                    .integrationId("qa")
                    .action("analyzeCoverage")
                    .parameters(Map.of("workingDir", "${workingDir}"))
                    .condition("${lastStepSuccess}")
                    .build()
            ))
            .build();
    }
    
    /**
     * Release workflow: Test → Build → Push → Deploy
     */
    public Workflow releaseWorkflow() {
        return Workflow.builder()
            .id("release")
            .name("Release Pipeline")
            .description("Full release pipeline with Docker and AWS deployment")
            .steps(List.of(
                WorkflowStep.builder()
                    .id("run-all-tests")
                    .name("Run All Tests")
                    .integrationId("qa")
                    .action("runTests")
                    .parameters(Map.of("workingDir", "${workingDir}"))
                    .build(),
                    
                WorkflowStep.builder()
                    .id("docker-build")
                    .name("Build Docker Image")
                    .integrationId("docker")
                    .action("build")
                    .parameters(Map.of(
                        "contextPath", "${workingDir}",
                        "imageName", "${registry}/${imageName}",
                        "tag", "${version}"
                    ))
                    .condition("${lastStepSuccess}")
                    .build(),
                    
                WorkflowStep.builder()
                    .id("docker-push")
                    .name("Push to ECR")
                    .integrationId("docker")
                    .action("push")
                    .parameters(Map.of(
                        "imageName", "${registry}/${imageName}",
                        "tag", "${version}"
                    ))
                    .condition("${lastStepSuccess}")
                    .build(),
                    
                WorkflowStep.builder()
                    .id("deploy-staging")
                    .name("Deploy to Staging")
                    .integrationId("aws")
                    .action("deployEcsService")
                    .parameters(Map.of(
                        "cluster", "${cluster}",
                        "service", "${service}-staging",
                        "taskDefinition", "${taskDefinition}:${version}",
                        "desiredCount", 1
                    ))
                    .condition("${lastStepSuccess}")
                    .build(),
                    
                WorkflowStep.builder()
                    .id("smoke-tests")
                    .name("Run Smoke Tests")
                    .integrationId("qa")
                    .action("runTests")
                    .parameters(Map.of(
                        "workingDir", "${workingDir}",
                        "testCommand", "${smokeTestCommand}"
                    ))
                    .condition("${lastStepSuccess}")
                    .maxRetries(2)
                    .build(),
                    
                WorkflowStep.builder()
                    .id("deploy-production")
                    .name("Deploy to Production")
                    .integrationId("aws")
                    .action("deployEcsService")
                    .parameters(Map.of(
                        "cluster", "${cluster}",
                        "service", "${service}",
                        "taskDefinition", "${taskDefinition}:${version}",
                        "desiredCount", "${desiredCount}"
                    ))
                    .condition("${lastStepSuccess}")
                    .build()
            ))
            .build();
    }
    
    /**
     * CI workflow: Lint → Test → Coverage
     */
    public Workflow ciWorkflow() {
        return Workflow.builder()
            .id("ci")
            .name("Continuous Integration")
            .description("Standard CI pipeline")
            .steps(List.of(
                WorkflowStep.builder()
                    .id("run-tests")
                    .name("Run Tests")
                    .integrationId("qa")
                    .action("runTests")
                    .parameters(Map.of("workingDir", "${workingDir}"))
                    .build(),
                    
                WorkflowStep.builder()
                    .id("coverage")
                    .name("Coverage Analysis")
                    .integrationId("qa")
                    .action("analyzeCoverage")
                    .parameters(Map.of("workingDir", "${workingDir}"))
                    .condition("${lastStepSuccess}")
                    .build(),
                    
                WorkflowStep.builder()
                    .id("docker-build")
                    .name("Build Docker Image")
                    .integrationId("docker")
                    .action("build")
                    .parameters(Map.of(
                        "contextPath", "${workingDir}",
                        "imageName", "${imageName}",
                        "tag", "ci-${buildNumber}"
                    ))
                    .condition("${lastStepSuccess}")
                    .build()
            ))
            .build();
    }
    
    /**
     * Get all predefined workflows.
     */
    public List<Workflow> getAll() {
        return List.of(
            bugFixWorkflow(),
            featureWorkflow(),
            releaseWorkflow(),
            ciWorkflow()
        );
    }
    
    /**
     * Get workflow by ID.
     */
    public Optional<Workflow> getById(String id) {
        return getAll().stream()
            .filter(w -> w.id().equals(id))
            .findFirst();
    }
}

