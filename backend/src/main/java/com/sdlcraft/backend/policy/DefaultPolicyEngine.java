package com.sdlcraft.backend.policy;

import com.sdlcraft.backend.intent.IntentResult;
import com.sdlcraft.backend.sdlc.Phase;
import com.sdlcraft.backend.sdlc.RiskLevel;
import com.sdlcraft.backend.sdlc.SDLCState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * DefaultPolicyEngine is the main implementation of PolicyEngine.
 * 
 * This service classifies commands by risk level and enforces safety policies.
 * It uses a rule-based approach to determine risk and checks both built-in
 * and custom policies.
 * 
 * Risk Classification Rules:
 * - HIGH: Production operations, delete operations, reset operations
 * - MEDIUM: Staging operations, bulk updates, schema changes
 * - LOW: Read operations, analysis, status checks
 * 
 * Requirements: 6.1, 6.2, 6.5, 6.6
 */
@Service
public class DefaultPolicyEngine implements PolicyEngine {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultPolicyEngine.class);
    
    // Thread-safe list for custom policies
    private final List<Policy> customPolicies = new CopyOnWriteArrayList<>();
    
    // Built-in policies
    private final List<Policy> builtInPolicies = new ArrayList<>();
    
    public DefaultPolicyEngine() {
        initializeBuiltInPolicies();
    }
    
    @Override
    public RiskAssessment assessRisk(IntentResult intent, SDLCState state) {
        logger.debug("Assessing risk for intent: {} target: {}", intent.getIntent(), intent.getTarget());
        
        RiskLevel riskLevel = classifyRisk(intent, state);
        RiskAssessment assessment = new RiskAssessment(riskLevel, buildExplanation(intent, state, riskLevel));
        
        // Add specific concerns
        addConcerns(assessment, intent, state);
        
        // Generate impact description
        assessment.setImpactDescription(generateImpactDescription(intent, state));
        
        // Determine if confirmation is required
        assessment.setRequiresConfirmation(
                riskLevel.isHigherThan(RiskLevel.MEDIUM) || 
                isProductionOperation(intent) ||
                isDestructiveOperation(intent)
        );
        
        logger.debug("Risk assessment: level={}, requiresConfirmation={}", 
                riskLevel, assessment.isRequiresConfirmation());
        
        return assessment;
    }
    
    @Override
    public boolean requiresConfirmation(RiskAssessment assessment) {
        return assessment.isRequiresConfirmation();
    }
    
    @Override
    public List<PolicyViolation> checkPolicies(IntentResult intent, SDLCState state) {
        logger.debug("Checking policies for intent: {}", intent.getIntent());
        
        List<PolicyViolation> violations = new ArrayList<>();
        
        // Check built-in policies
        for (Policy policy : builtInPolicies) {
            PolicyViolation violation = policy.check(intent, state);
            if (violation != null) {
                violations.add(violation);
                logger.warn("Policy violation: {} - {}", policy.getName(), violation.getMessage());
            }
        }
        
        // Check custom policies
        for (Policy policy : customPolicies) {
            PolicyViolation violation = policy.check(intent, state);
            if (violation != null) {
                violations.add(violation);
                logger.warn("Custom policy violation: {} - {}", policy.getName(), violation.getMessage());
            }
        }
        
        return violations;
    }
    
    @Override
    public void registerPolicy(Policy policy) {
        logger.info("Registering custom policy: {}", policy.getName());
        customPolicies.add(policy);
    }
    
    @Override
    public List<Policy> getAllPolicies() {
        List<Policy> allPolicies = new ArrayList<>(builtInPolicies);
        allPolicies.addAll(customPolicies);
        return allPolicies;
    }
    
    // Private helper methods
    
    private void initializeBuiltInPolicies() {
        // Policy 1: No production deployments on Fridays
        builtInPolicies.add(new Policy() {
            @Override
            public String getName() {
                return "no-friday-deployments";
            }
            
            @Override
            public String getDescription() {
                return "Production deployments are not allowed on Fridays";
            }
            
            @Override
            public PolicyViolation check(IntentResult intent, SDLCState state) {
                if ("release".equals(intent.getIntent()) && 
                    "production".equals(intent.getTarget()) &&
                    LocalDateTime.now().getDayOfWeek() == DayOfWeek.FRIDAY) {
                    PolicyViolation violation = new PolicyViolation(
                            getName(),
                            "Production deployments on Fridays are discouraged to avoid weekend issues",
                            PolicySeverity.WARNING
                    );
                    violation.setSuggestedAction("Deploy on Monday-Thursday or wait until Monday");
                    return violation;
                }
                return null;
            }
            
            @Override
            public PolicySeverity getSeverity() {
                return PolicySeverity.WARNING;
            }
        });
        
        // Policy 2: Require minimum test coverage for production
        builtInPolicies.add(new Policy() {
            @Override
            public String getName() {
                return "minimum-coverage-production";
            }
            
            @Override
            public String getDescription() {
                return "Production deployments require minimum 70% test coverage";
            }
            
            @Override
            public PolicyViolation check(IntentResult intent, SDLCState state) {
                if ("release".equals(intent.getIntent()) && 
                    "production".equals(intent.getTarget()) &&
                    state.getTestCoverage() != null &&
                    state.getTestCoverage() < 0.7) {
                    PolicyViolation violation = new PolicyViolation(
                            getName(),
                            String.format("Test coverage is %.1f%%, minimum 70%% required for production", 
                                    state.getTestCoverage() * 100),
                            PolicySeverity.ERROR
                    );
                    violation.setSuggestedAction("Increase test coverage before deploying to production");
                    return violation;
                }
                return null;
            }
            
            @Override
            public PolicySeverity getSeverity() {
                return PolicySeverity.ERROR;
            }
        });
        
        // Policy 3: No production operations in PLANNING phase
        builtInPolicies.add(new Policy() {
            @Override
            public String getName() {
                return "no-production-in-planning";
            }
            
            @Override
            public String getDescription() {
                return "Production operations are not allowed during PLANNING phase";
            }
            
            @Override
            public PolicyViolation check(IntentResult intent, SDLCState state) {
                if ("production".equals(intent.getTarget()) &&
                    state.getCurrentPhase() == Phase.PLANNING) {
                    PolicyViolation violation = new PolicyViolation(
                            getName(),
                            "Cannot perform production operations during PLANNING phase",
                            PolicySeverity.ERROR
                    );
                    violation.setSuggestedAction("Transition to DEVELOPMENT phase first");
                    return violation;
                }
                return null;
            }
            
            @Override
            public PolicySeverity getSeverity() {
                return PolicySeverity.ERROR;
            }
        });
        
        logger.info("Initialized {} built-in policies", builtInPolicies.size());
    }
    
    private RiskLevel classifyRisk(IntentResult intent, SDLCState state) {
        String intentName = intent.getIntent();
        String target = intent.getTarget();
        
        // HIGH RISK: Production operations, destructive operations
        if (isProductionOperation(intent) || isDestructiveOperation(intent)) {
            return RiskLevel.HIGH;
        }
        
        // HIGH RISK: Release intent
        if ("release".equals(intentName)) {
            return RiskLevel.HIGH;
        }
        
        // MEDIUM RISK: Staging operations, improve/prepare intents
        if ("staging".equals(target) || 
            "improve".equals(intentName) || 
            "prepare".equals(intentName)) {
            return RiskLevel.MEDIUM;
        }
        
        // Consider current state risk level
        if (state != null && state.getRiskLevel() != null) {
            RiskLevel stateRisk = state.getRiskLevel();
            
            // If state is already high risk, elevate command risk
            if (stateRisk.isHigherThan(RiskLevel.MEDIUM)) {
                return RiskLevel.MEDIUM;
            }
        }
        
        // LOW RISK: Read operations, analysis, status checks
        return RiskLevel.LOW;
    }
    
    private boolean isProductionOperation(IntentResult intent) {
        return "production".equalsIgnoreCase(intent.getTarget());
    }
    
    private boolean isDestructiveOperation(IntentResult intent) {
        String intentName = intent.getIntent().toLowerCase();
        return intentName.contains("delete") || 
               intentName.contains("reset") || 
               intentName.contains("destroy") ||
               intentName.contains("remove");
    }
    
    private String buildExplanation(IntentResult intent, SDLCState state, RiskLevel riskLevel) {
        StringBuilder explanation = new StringBuilder();
        
        explanation.append("Risk level: ").append(riskLevel.getDisplayName()).append(". ");
        
        switch (riskLevel) {
            case LOW:
                explanation.append("This is a safe operation with minimal impact.");
                break;
            case MEDIUM:
                explanation.append("This operation has moderate impact and should be reviewed.");
                break;
            case HIGH:
                explanation.append("This is a high-risk operation that requires careful consideration.");
                break;
            case CRITICAL:
                explanation.append("This is a critical operation that could have severe consequences.");
                break;
        }
        
        if (isProductionOperation(intent)) {
            explanation.append(" Production environment will be affected.");
        }
        
        if (state != null && state.getRiskLevel() != null && 
            state.getRiskLevel().isHigherThan(RiskLevel.MEDIUM)) {
            explanation.append(" Current project state has elevated risk (")
                      .append(state.getRiskLevel().getDisplayName())
                      .append(").");
        }
        
        return explanation.toString();
    }
    
    private void addConcerns(RiskAssessment assessment, IntentResult intent, SDLCState state) {
        if (isProductionOperation(intent)) {
            assessment.addConcern("Production environment will be modified");
        }
        
        if (isDestructiveOperation(intent)) {
            assessment.addConcern("This is a destructive operation that cannot be easily undone");
        }
        
        if (state != null) {
            if (state.getTestCoverage() != null && state.getTestCoverage() < 0.7) {
                assessment.addConcern(String.format("Test coverage is low (%.1f%%)", 
                        state.getTestCoverage() * 100));
            }
            
            if (state.getOpenIssues() != null && state.getOpenIssues() > 10) {
                assessment.addConcern(String.format("%d open issues in project", state.getOpenIssues()));
            }
            
            if (state.getRiskLevel() != null && state.getRiskLevel().isHigherThan(RiskLevel.MEDIUM)) {
                assessment.addConcern("Project is in elevated risk state");
            }
        }
    }
    
    private String generateImpactDescription(IntentResult intent, SDLCState state) {
        StringBuilder impact = new StringBuilder();
        
        String intentName = intent.getIntent();
        String target = intent.getTarget();
        
        if ("release".equals(intentName)) {
            impact.append("This will deploy the application to ").append(target).append(". ");
            if ("production".equals(target)) {
                impact.append("End users will be affected. ");
            }
        } else if ("improve".equals(intentName)) {
            impact.append("This will modify ").append(target).append(" in the codebase. ");
        } else if ("test".equals(intentName)) {
            impact.append("This will run ").append(target).append(" tests. ");
        } else if ("analyze".equals(intentName)) {
            impact.append("This will analyze ").append(target).append(". ");
        }
        
        if (state != null && state.getCurrentPhase() != null) {
            impact.append("Current phase: ").append(state.getCurrentPhase().getDisplayName()).append(".");
        }
        
        return impact.toString();
    }
}
