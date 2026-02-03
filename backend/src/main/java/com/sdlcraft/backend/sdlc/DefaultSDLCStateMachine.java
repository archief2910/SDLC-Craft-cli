package com.sdlcraft.backend.sdlc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DefaultSDLCStateMachine is the main implementation of SDLCStateMachine.
 * 
 * This service manages project state through the SDLC, calculating risk scores
 * and release readiness based on multiple factors. It persists all state changes
 * to PostgreSQL and emits events for audit logging.
 * 
 * Risk Calculation Formula:
 * Risk Score = (1 - testCoverage) * 0.4 + (openIssues / totalIssues) * 0.3 +
 *              (daysSinceLastDeploy / 30) * 0.2 + customRiskFactors * 0.1
 * 
 * Release Readiness Formula:
 * Readiness = testCoverage * 0.4 + (1 - openIssues / totalIssues) * 0.3 +
 *             (1 - daysSinceLastDeploy / 30) * 0.2 + customReadinessFactors * 0.1
 * 
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5
 */
@Service
@Transactional
public class DefaultSDLCStateMachine implements SDLCStateMachine {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultSDLCStateMachine.class);
    
    private final SDLCStateRepository stateRepository;
    
    // Weights for risk calculation
    private static final double COVERAGE_WEIGHT = 0.4;
    private static final double ISSUES_WEIGHT = 0.3;
    private static final double DEPLOYMENT_WEIGHT = 0.2;
    private static final double CUSTOM_WEIGHT = 0.1;
    
    // Thresholds
    private static final int MAX_DAYS_SINCE_DEPLOYMENT = 30;
    
    @Autowired
    public DefaultSDLCStateMachine(SDLCStateRepository stateRepository) {
        this.stateRepository = stateRepository;
    }
    
    @Override
    public SDLCState getCurrentState(String projectId) {
        logger.debug("Getting current state for project: {}", projectId);
        
        SDLCStateEntity entity = stateRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        
        return entity.toSDLCState();
    }
    
    @Override
    public void transitionTo(String projectId, Phase newPhase) {
        logger.info("Transitioning project {} to phase {}", projectId, newPhase);
        
        SDLCStateEntity entity = stateRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        
        Phase currentPhase = entity.getCurrentPhase();
        
        // Validate transition
        if (!currentPhase.canTransitionTo(newPhase)) {
            throw new InvalidPhaseTransitionException(currentPhase, newPhase);
        }
        
        // Update phase
        entity.setCurrentPhase(newPhase);
        entity.setUpdatedAt(LocalDateTime.now());
        
        // Recalculate risk and readiness
        recalculateMetrics(entity);
        
        stateRepository.save(entity);
        
        logger.info("Project {} transitioned from {} to {}", projectId, currentPhase, newPhase);
        
        // TODO: Emit state change event for audit logging (Task 11)
    }
    
    @Override
    public void updateMetrics(String projectId, Metrics metrics) {
        logger.debug("Updating metrics for project: {}", projectId);
        
        SDLCStateEntity entity = stateRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        
        // Update metrics
        if (metrics.getTestCoverage() != null) {
            entity.setTestCoverage(metrics.getTestCoverage());
        }
        if (metrics.getOpenIssues() != null) {
            entity.setOpenIssues(metrics.getOpenIssues());
        }
        if (metrics.getTotalIssues() != null) {
            entity.setTotalIssues(metrics.getTotalIssues());
        }
        if (metrics.getLastDeployment() != null) {
            entity.setLastDeployment(metrics.getLastDeployment());
        }
        if (metrics.getCustomMetrics() != null && !metrics.getCustomMetrics().isEmpty()) {
            entity.getCustomMetrics().putAll(metrics.getCustomMetrics());
        }
        
        // Recalculate risk and readiness
        recalculateMetrics(entity);
        
        entity.setUpdatedAt(LocalDateTime.now());
        stateRepository.save(entity);
        
        logger.debug("Metrics updated for project {}: coverage={}, openIssues={}, risk={}", 
                projectId, entity.getTestCoverage(), entity.getOpenIssues(), entity.getRiskLevel());
    }
    
    @Override
    public ReleaseReadiness calculateReadiness(String projectId) {
        SDLCStateEntity entity = stateRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        
        double score = calculateReadinessScore(entity);
        ReleaseReadiness.ReadinessStatus status = ReleaseReadiness.ReadinessStatus.fromScore(score);
        
        // Determine ready and blocking factors
        List<String> readyFactors = new ArrayList<>();
        List<String> blockingFactors = new ArrayList<>();
        
        // Check test coverage
        if (entity.getTestCoverage() != null) {
            if (entity.getTestCoverage() >= 0.8) {
                readyFactors.add("Test coverage is " + String.format("%.1f%%", entity.getTestCoverage() * 100));
            } else {
                blockingFactors.add("Test coverage is only " + String.format("%.1f%%", entity.getTestCoverage() * 100) + " (target: 80%)");
            }
        } else {
            blockingFactors.add("Test coverage not measured");
        }
        
        // Check open issues
        if (entity.getOpenIssues() != null) {
            if (entity.getOpenIssues() == 0) {
                readyFactors.add("No open issues");
            } else if (entity.getOpenIssues() <= 5) {
                readyFactors.add("Only " + entity.getOpenIssues() + " open issues");
            } else {
                blockingFactors.add(entity.getOpenIssues() + " open issues need resolution");
            }
        }
        
        // Check deployment history
        if (entity.getLastDeployment() != null) {
            long daysSince = ChronoUnit.DAYS.between(entity.getLastDeployment(), LocalDateTime.now());
            if (daysSince <= 7) {
                readyFactors.add("Recently deployed (" + daysSince + " days ago)");
            } else if (daysSince > 30) {
                blockingFactors.add("No deployment in " + daysSince + " days");
            }
        } else {
            blockingFactors.add("No deployment history");
        }
        
        // Check phase
        if (entity.getCurrentPhase() == Phase.PRODUCTION) {
            readyFactors.add("Already in production");
        } else if (entity.getCurrentPhase() == Phase.STAGING) {
            readyFactors.add("In staging phase");
        } else if (entity.getCurrentPhase() == Phase.PLANNING || entity.getCurrentPhase() == Phase.DEVELOPMENT) {
            blockingFactors.add("Still in " + entity.getCurrentPhase() + " phase");
        }
        
        return new ReleaseReadiness(score, status, readyFactors, blockingFactors);
    }
    
    @Override
    public double calculateRiskScore(String projectId) {
        SDLCStateEntity entity = stateRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        
        return calculateRiskScoreInternal(entity);
    }
    
    @Override
    public SDLCState initializeProject(String projectId) {
        logger.info("Initializing project: {}", projectId);
        
        // Check if project already exists
        if (stateRepository.existsById(projectId)) {
            logger.warn("Project {} already exists, returning existing state", projectId);
            return getCurrentState(projectId);
        }
        
        // Create new state
        SDLCStateEntity entity = new SDLCStateEntity(projectId, Phase.PLANNING);
        entity.setRiskLevel(RiskLevel.LOW);
        entity.setTestCoverage(0.0);
        entity.setOpenIssues(0);
        entity.setTotalIssues(0);
        entity.setReleaseReadiness(0.0);
        entity.setCustomMetrics(new HashMap<>());
        
        stateRepository.save(entity);
        
        logger.info("Project {} initialized in PLANNING phase", projectId);
        
        return entity.toSDLCState();
    }
    
    @Override
    public void addCustomMetric(String projectId, String key, Object value) {
        logger.debug("Adding custom metric {} for project {}", key, projectId);
        
        SDLCStateEntity entity = stateRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        
        entity.getCustomMetrics().put(key, value);
        entity.setUpdatedAt(LocalDateTime.now());
        
        // Recalculate if metric affects risk/readiness
        if (key.startsWith("risk_") || key.startsWith("readiness_")) {
            recalculateMetrics(entity);
        }
        
        stateRepository.save(entity);
    }
    
    @Override
    public Map<String, Object> getCustomMetrics(String projectId) {
        SDLCStateEntity entity = stateRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        
        return new HashMap<>(entity.getCustomMetrics());
    }
    
    // Private helper methods
    
    private void recalculateMetrics(SDLCStateEntity entity) {
        // Calculate risk score
        double riskScore = calculateRiskScoreInternal(entity);
        entity.setRiskLevel(RiskLevel.fromScore(riskScore));
        
        // Calculate release readiness
        double readiness = calculateReadinessScore(entity);
        entity.setReleaseReadiness(readiness);
    }
    
    private double calculateRiskScoreInternal(SDLCStateEntity entity) {
        double riskScore = 0.0;
        
        // Factor 1: Test coverage (lower coverage = higher risk)
        double coverageRisk = 0.0;
        if (entity.getTestCoverage() != null) {
            coverageRisk = (1.0 - entity.getTestCoverage()) * COVERAGE_WEIGHT;
        } else {
            coverageRisk = COVERAGE_WEIGHT; // No coverage data = maximum risk
        }
        
        // Factor 2: Open issues (more issues = higher risk)
        double issuesRisk = 0.0;
        if (entity.getOpenIssues() != null && entity.getTotalIssues() != null && entity.getTotalIssues() > 0) {
            issuesRisk = ((double) entity.getOpenIssues() / entity.getTotalIssues()) * ISSUES_WEIGHT;
        }
        
        // Factor 3: Time since last deployment (longer time = higher risk)
        double deploymentRisk = 0.0;
        if (entity.getLastDeployment() != null) {
            long daysSince = ChronoUnit.DAYS.between(entity.getLastDeployment(), LocalDateTime.now());
            deploymentRisk = Math.min(1.0, (double) daysSince / MAX_DAYS_SINCE_DEPLOYMENT) * DEPLOYMENT_WEIGHT;
        } else {
            deploymentRisk = DEPLOYMENT_WEIGHT; // No deployment data = maximum risk
        }
        
        // Factor 4: Custom risk factors
        double customRisk = calculateCustomRiskFactor(entity) * CUSTOM_WEIGHT;
        
        riskScore = coverageRisk + issuesRisk + deploymentRisk + customRisk;
        
        // Clamp to [0.0, 1.0]
        return Math.max(0.0, Math.min(1.0, riskScore));
    }
    
    private double calculateReadinessScore(SDLCStateEntity entity) {
        double readiness = 0.0;
        
        // Factor 1: Test coverage (higher coverage = more ready)
        double coverageReadiness = 0.0;
        if (entity.getTestCoverage() != null) {
            coverageReadiness = entity.getTestCoverage() * COVERAGE_WEIGHT;
        }
        
        // Factor 2: Open issues (fewer issues = more ready)
        double issuesReadiness = 0.0;
        if (entity.getOpenIssues() != null && entity.getTotalIssues() != null && entity.getTotalIssues() > 0) {
            issuesReadiness = (1.0 - ((double) entity.getOpenIssues() / entity.getTotalIssues())) * ISSUES_WEIGHT;
        } else if (entity.getOpenIssues() != null && entity.getOpenIssues() == 0) {
            issuesReadiness = ISSUES_WEIGHT; // No issues = maximum readiness
        }
        
        // Factor 3: Recent deployment (recent deployment = more ready)
        double deploymentReadiness = 0.0;
        if (entity.getLastDeployment() != null) {
            long daysSince = ChronoUnit.DAYS.between(entity.getLastDeployment(), LocalDateTime.now());
            deploymentReadiness = (1.0 - Math.min(1.0, (double) daysSince / MAX_DAYS_SINCE_DEPLOYMENT)) * DEPLOYMENT_WEIGHT;
        }
        
        // Factor 4: Custom readiness factors
        double customReadiness = calculateCustomReadinessFactor(entity) * CUSTOM_WEIGHT;
        
        readiness = coverageReadiness + issuesReadiness + deploymentReadiness + customReadiness;
        
        // Clamp to [0.0, 1.0]
        return Math.max(0.0, Math.min(1.0, readiness));
    }
    
    private double calculateCustomRiskFactor(SDLCStateEntity entity) {
        // Extract custom risk factors from custom metrics
        double customRisk = 0.0;
        int count = 0;
        
        for (Map.Entry<String, Object> entry : entity.getCustomMetrics().entrySet()) {
            if (entry.getKey().startsWith("risk_") && entry.getValue() instanceof Number) {
                customRisk += ((Number) entry.getValue()).doubleValue();
                count++;
            }
        }
        
        return count > 0 ? customRisk / count : 0.0;
    }
    
    private double calculateCustomReadinessFactor(SDLCStateEntity entity) {
        // Extract custom readiness factors from custom metrics
        double customReadiness = 0.0;
        int count = 0;
        
        for (Map.Entry<String, Object> entry : entity.getCustomMetrics().entrySet()) {
            if (entry.getKey().startsWith("readiness_") && entry.getValue() instanceof Number) {
                customReadiness += ((Number) entry.getValue()).doubleValue();
                count++;
            }
        }
        
        return count > 0 ? customReadiness / count : 0.0;
    }
}
