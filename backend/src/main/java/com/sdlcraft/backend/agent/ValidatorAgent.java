package com.sdlcraft.backend.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ValidatorAgent validates execution results against expected criteria.
 * 
 * The validator checks whether actions produced the expected outcomes,
 * verifies state changes, and identifies any anomalies or failures.
 * 
 * Design rationale:
 * - Validates each step's output against success criteria
 * - Identifies anomalies even in "successful" executions
 * - Provides detailed validation findings for reflection
 * - Supports both hard failures and soft warnings
 */
@Component
public class ValidatorAgent implements Agent {
    
    private static final Logger logger = LoggerFactory.getLogger(ValidatorAgent.class);
    
    private static final String AGENT_TYPE = "validator";
    
    @Override
    public String getType() {
        return AGENT_TYPE;
    }
    
    @Override
    public AgentResult plan(AgentContext context) {
        // Validator doesn't create plans
        return AgentResult.builder()
                .agentType(AGENT_TYPE)
                .phase(AgentPhase.PLAN)
                .status(AgentStatus.SKIPPED)
                .reasoning("Validator agent does not create plans")
                .build();
    }
    
    @Override
    public AgentResult act(AgentContext context) {
        // Validator doesn't execute actions
        return AgentResult.builder()
                .agentType(AGENT_TYPE)
                .phase(AgentPhase.ACT)
                .status(AgentStatus.SKIPPED)
                .reasoning("Validator agent does not execute actions")
                .build();
    }
    
    @Override
    public AgentResult observe(AgentContext context) {
        logger.info("Validating execution results for intent: {}", context.getIntent().getIntent());
        
        try {
            // Get execution results from executor
            AgentResult executorResult = context.getLastResultByType("executor");
            if (executorResult == null) {
                return AgentResult.builder()
                        .agentType(AGENT_TYPE)
                        .phase(AgentPhase.OBSERVE)
                        .status(AgentStatus.FAILURE)
                        .error("No execution results found")
                        .reasoning("Cannot validate without execution results from executor agent")
                        .build();
            }
            
            // Check if execution succeeded
            if (executorResult.isFailure()) {
                return AgentResult.builder()
                        .agentType(AGENT_TYPE)
                        .phase(AgentPhase.OBSERVE)
                        .status(AgentStatus.FAILURE)
                        .error("Execution failed, cannot validate")
                        .reasoning("Executor reported failure: " + executorResult.getError())
                        .build();
            }
            
            // Validate execution results
            List<ValidationFinding> findings = new ArrayList<>();
            
            // Get step results
            @SuppressWarnings("unchecked")
            List<ExecutorAgent.StepResult> stepResults = 
                    (List<ExecutorAgent.StepResult>) executorResult.getData("stepResults");
            
            if (stepResults != null) {
                for (ExecutorAgent.StepResult stepResult : stepResults) {
                    ValidationFinding finding = validateStepResult(stepResult, context);
                    if (finding != null) {
                        findings.add(finding);
                    }
                }
            }
            
            // Validate overall execution data
            @SuppressWarnings("unchecked")
            Map<String, Object> executionData = 
                    (Map<String, Object>) executorResult.getData("executionData");
            
            if (executionData != null) {
                ValidationFinding dataFinding = validateExecutionData(executionData, context);
                if (dataFinding != null) {
                    findings.add(dataFinding);
                }
            }
            
            // Determine overall validation status
            AgentStatus status = determineValidationStatus(findings);
            String reasoning = buildValidationReasoning(findings, status);
            
            return AgentResult.builder()
                    .agentType(AGENT_TYPE)
                    .phase(AgentPhase.OBSERVE)
                    .status(status)
                    .data("findings", findings)
                    .data("totalFindings", findings.size())
                    .data("criticalFindings", countCriticalFindings(findings))
                    .reasoning(reasoning)
                    .build();
                    
        } catch (Exception e) {
            logger.error("Validation failed", e);
            return AgentResult.builder()
                    .agentType(AGENT_TYPE)
                    .phase(AgentPhase.OBSERVE)
                    .status(AgentStatus.FAILURE)
                    .error("Validation failed: " + e.getMessage())
                    .reasoning("Exception occurred during validation: " + e.getMessage())
                    .build();
        }
    }
    
    @Override
    public AgentResult reflect(AgentContext context) {
        // Validator doesn't reflect on outcomes
        return AgentResult.builder()
                .agentType(AGENT_TYPE)
                .phase(AgentPhase.REFLECT)
                .status(AgentStatus.SKIPPED)
                .reasoning("Validator agent does not reflect on outcomes")
                .build();
    }
    
    @Override
    public boolean canHandle(AgentContext context) {
        // Validator can handle any context that has execution results
        AgentResult executorResult = context.getLastResultByType("executor");
        return executorResult != null;
    }
    
    /**
     * Validate a single step result.
     */
    private ValidationFinding validateStepResult(ExecutorAgent.StepResult stepResult, AgentContext context) {
        if (!stepResult.isSuccess()) {
            return new ValidationFinding(
                    ValidationSeverity.CRITICAL,
                    "Step failed: " + stepResult.getStepId(),
                    stepResult.getError(),
                    "Fix the error and retry execution"
            );
        }
        
        // Check for anomalies in successful steps
        Object output = stepResult.getOutput();
        if (output == null) {
            return new ValidationFinding(
                    ValidationSeverity.WARNING,
                    "Step produced no output: " + stepResult.getStepId(),
                    "Step completed but did not produce expected output",
                    "Verify that step is functioning correctly"
            );
        }
        
        // Validate output structure if it's a map
        if (output instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> outputMap = (Map<String, Object>) output;
            
            // Check for error indicators in output
            if (outputMap.containsKey("error") || outputMap.containsKey("exitCode")) {
                Object exitCode = outputMap.get("exitCode");
                if (exitCode instanceof Integer && (Integer) exitCode != 0) {
                    return new ValidationFinding(
                            ValidationSeverity.ERROR,
                            "Step reported non-zero exit code: " + stepResult.getStepId(),
                            "Exit code: " + exitCode,
                            "Check step logs for details"
                    );
                }
            }
            
            // Check for warnings in output
            if (outputMap.containsKey("warnings")) {
                Object warnings = outputMap.get("warnings");
                if (warnings instanceof Integer && (Integer) warnings > 0) {
                    return new ValidationFinding(
                            ValidationSeverity.INFO,
                            "Step produced warnings: " + stepResult.getStepId(),
                            warnings + " warnings found",
                            "Review warnings to ensure they are acceptable"
                    );
                }
            }
        }
        
        return null; // No issues found
    }
    
    /**
     * Validate overall execution data.
     */
    private ValidationFinding validateExecutionData(Map<String, Object> executionData, AgentContext context) {
        if (executionData.isEmpty()) {
            return new ValidationFinding(
                    ValidationSeverity.WARNING,
                    "Execution produced no data",
                    "No data was collected during execution",
                    "Verify that execution steps are producing expected outputs"
            );
        }
        
        // Check for expected data based on intent
        String intent = context.getIntent().getIntent();
        
        if ("status".equals(intent)) {
            if (!executionData.containsKey("query-state")) {
                return new ValidationFinding(
                        ValidationSeverity.ERROR,
                        "Status query did not produce state data",
                        "Expected state data not found in execution results",
                        "Verify state machine is accessible"
                );
            }
        }
        
        return null; // No issues found
    }
    
    /**
     * Determine overall validation status from findings.
     */
    private AgentStatus determineValidationStatus(List<ValidationFinding> findings) {
        if (findings.isEmpty()) {
            return AgentStatus.SUCCESS;
        }
        
        boolean hasCritical = false;
        boolean hasError = false;
        boolean hasWarning = false;
        
        for (ValidationFinding finding : findings) {
            switch (finding.getSeverity()) {
                case CRITICAL:
                    hasCritical = true;
                    break;
                case ERROR:
                    hasError = true;
                    break;
                case WARNING:
                    hasWarning = true;
                    break;
                case INFO:
                    // Info doesn't affect status
                    break;
            }
        }
        
        if (hasCritical || hasError) {
            return AgentStatus.FAILURE;
        } else if (hasWarning) {
            return AgentStatus.PARTIAL;
        } else {
            return AgentStatus.SUCCESS;
        }
    }
    
    /**
     * Count critical findings.
     */
    private int countCriticalFindings(List<ValidationFinding> findings) {
        int count = 0;
        for (ValidationFinding finding : findings) {
            if (finding.getSeverity() == ValidationSeverity.CRITICAL || 
                finding.getSeverity() == ValidationSeverity.ERROR) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Build reasoning explanation for validation.
     */
    private String buildValidationReasoning(List<ValidationFinding> findings, AgentStatus status) {
        StringBuilder reasoning = new StringBuilder();
        
        if (findings.isEmpty()) {
            reasoning.append("Validation passed with no issues found. ");
            reasoning.append("All execution results meet expected criteria.");
        } else {
            reasoning.append("Validation found ").append(findings.size()).append(" finding(s): ");
            
            int critical = 0, error = 0, warning = 0, info = 0;
            for (ValidationFinding finding : findings) {
                switch (finding.getSeverity()) {
                    case CRITICAL: critical++; break;
                    case ERROR: error++; break;
                    case WARNING: warning++; break;
                    case INFO: info++; break;
                }
            }
            
            List<String> parts = new ArrayList<>();
            if (critical > 0) parts.add(critical + " critical");
            if (error > 0) parts.add(error + " error");
            if (warning > 0) parts.add(warning + " warning");
            if (info > 0) parts.add(info + " info");
            
            reasoning.append(String.join(", ", parts)).append(". ");
            
            if (status == AgentStatus.FAILURE) {
                reasoning.append("Execution did not meet success criteria.");
            } else if (status == AgentStatus.PARTIAL) {
                reasoning.append("Execution completed with warnings.");
            }
        }
        
        return reasoning.toString();
    }
    
    /**
     * Validation finding with severity and details.
     */
    public static class ValidationFinding {
        private final ValidationSeverity severity;
        private final String title;
        private final String description;
        private final String recommendation;
        
        public ValidationFinding(ValidationSeverity severity, String title, 
                               String description, String recommendation) {
            this.severity = severity;
            this.title = title;
            this.description = description;
            this.recommendation = recommendation;
        }
        
        public ValidationSeverity getSeverity() {
            return severity;
        }
        
        public String getTitle() {
            return title;
        }
        
        public String getDescription() {
            return description;
        }
        
        public String getRecommendation() {
            return recommendation;
        }
    }
    
    /**
     * Severity levels for validation findings.
     */
    public enum ValidationSeverity {
        CRITICAL,  // Execution failed completely
        ERROR,     // Significant issue that should be addressed
        WARNING,   // Minor issue or potential problem
        INFO       // Informational finding
    }
}
