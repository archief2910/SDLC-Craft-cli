package com.sdlcraft.backend.controller;

import com.sdlcraft.backend.integration.IntegrationHealth;
import com.sdlcraft.backend.workflow.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for workflow execution and integration management.
 */
@RestController
@RequestMapping("/api/workflow")
public class WorkflowController {

    private final WorkflowExecutor workflowExecutor;
    private final PredefinedWorkflows predefinedWorkflows;
    private final Map<String, CompletableFuture<WorkflowResult>> runningWorkflows = new HashMap<>();

    public WorkflowController(WorkflowExecutor workflowExecutor, PredefinedWorkflows predefinedWorkflows) {
        this.workflowExecutor = workflowExecutor;
        this.predefinedWorkflows = predefinedWorkflows;
    }

    /**
     * List available workflows.
     */
    @GetMapping
    public ResponseEntity<List<WorkflowSummary>> listWorkflows() {
        List<WorkflowSummary> summaries = predefinedWorkflows.getAll().stream()
            .map(w -> new WorkflowSummary(w.id(), w.name(), w.description(), w.steps().size()))
            .toList();
        return ResponseEntity.ok(summaries);
    }

    /**
     * Get workflow details.
     */
    @GetMapping("/{workflowId}")
    public ResponseEntity<Workflow> getWorkflow(@PathVariable String workflowId) {
        return predefinedWorkflows.getById(workflowId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Execute a workflow synchronously.
     */
    @PostMapping("/{workflowId}/execute")
    public ResponseEntity<WorkflowResult> executeWorkflow(
            @PathVariable String workflowId,
            @RequestBody Map<String, Object> context) {
        
        return predefinedWorkflows.getById(workflowId)
            .map(workflow -> {
                WorkflowResult result = workflowExecutor.execute(workflow, context);
                return ResponseEntity.ok(result);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Execute a workflow asynchronously.
     */
    @PostMapping("/{workflowId}/execute-async")
    public ResponseEntity<Map<String, String>> executeWorkflowAsync(
            @PathVariable String workflowId,
            @RequestBody Map<String, Object> context) {
        
        return predefinedWorkflows.getById(workflowId)
            .map(workflow -> {
                String executionId = UUID.randomUUID().toString();
                CompletableFuture<WorkflowResult> future = workflowExecutor.executeAsync(workflow, context);
                runningWorkflows.put(executionId, future);
                
                return ResponseEntity.ok(Map.of(
                    "executionId", executionId,
                    "status", "STARTED",
                    "workflowId", workflowId
                ));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get async execution status.
     */
    @GetMapping("/execution/{executionId}")
    public ResponseEntity<Map<String, Object>> getExecutionStatus(@PathVariable String executionId) {
        CompletableFuture<WorkflowResult> future = runningWorkflows.get(executionId);
        
        if (future == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> status = new HashMap<>();
        status.put("executionId", executionId);
        
        if (future.isDone()) {
            try {
                WorkflowResult result = future.get();
                status.put("status", "COMPLETED");
                status.put("success", result.success());
                status.put("summary", result.summary());
                status.put("result", result);
            } catch (Exception e) {
                status.put("status", "FAILED");
                status.put("error", e.getMessage());
            }
        } else {
            status.put("status", "RUNNING");
        }
        
        return ResponseEntity.ok(status);
    }

    /**
     * Get integration health status.
     */
    @GetMapping("/integrations/health")
    public ResponseEntity<Map<String, IntegrationHealth>> getIntegrationHealth() {
        return ResponseEntity.ok(workflowExecutor.getIntegrationHealth());
    }

    /**
     * Execute custom workflow (not predefined).
     */
    @PostMapping("/custom/execute")
    public ResponseEntity<WorkflowResult> executeCustomWorkflow(
            @RequestBody CustomWorkflowRequest request) {
        
        Workflow workflow = Workflow.builder()
            .id("custom-" + UUID.randomUUID().toString().substring(0, 8))
            .name(request.name())
            .description(request.description())
            .steps(request.steps())
            .build();
        
        WorkflowResult result = workflowExecutor.execute(workflow, request.context());
        return ResponseEntity.ok(result);
    }

    // DTOs
    record WorkflowSummary(String id, String name, String description, int stepCount) {}
    
    record CustomWorkflowRequest(
        String name,
        String description,
        List<WorkflowStep> steps,
        Map<String, Object> context
    ) {}
}

