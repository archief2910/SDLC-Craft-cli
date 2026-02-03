package com.sdlcraft.backend.sdlc;

import java.util.Map;

/**
 * SDLCStateMachine manages the state of projects through the SDLC.
 * 
 * This service is responsible for:
 * - Tracking current phase and metrics
 * - Managing phase transitions
 * - Calculating risk scores and release readiness
 * - Persisting state changes
 * - Emitting events on state changes
 * 
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7
 */
public interface SDLCStateMachine {
    
    /**
     * Gets the current state for a project.
     * 
     * @param projectId the project identifier
     * @return the current SDLC state
     * @throws ProjectNotFoundException if project not found
     */
    SDLCState getCurrentState(String projectId);
    
    /**
     * Transitions a project to a new phase.
     * 
     * This method validates that the transition is valid (forward progression or
     * backward rollback) and updates the state accordingly. It also emits a state
     * change event for audit logging.
     * 
     * @param projectId the project identifier
     * @param newPhase the target phase
     * @throws InvalidPhaseTransitionException if transition is not valid
     */
    void transitionTo(String projectId, Phase newPhase);
    
    /**
     * Updates metrics for a project.
     * 
     * This method updates test coverage, open issues, and other metrics, then
     * recalculates the risk level and release readiness.
     * 
     * @param projectId the project identifier
     * @param metrics the metrics to update
     */
    void updateMetrics(String projectId, Metrics metrics);
    
    /**
     * Calculates the release readiness score for a project.
     * 
     * Release readiness is calculated based on:
     * - Test coverage (40% weight)
     * - Open issues (30% weight)
     * - Time since last deployment (20% weight)
     * - Custom risk factors (10% weight)
     * 
     * Score ranges from 0.0 (not ready) to 1.0 (fully ready).
     * 
     * @param projectId the project identifier
     * @return the release readiness assessment with score, status, and factors
     */
    ReleaseReadiness calculateReadiness(String projectId);
    
    /**
     * Calculates the risk score for a project.
     * 
     * Risk score is calculated based on:
     * - Test coverage (lower coverage = higher risk)
     * - Open issues (more issues = higher risk)
     * - Time since last deployment (longer time = higher risk)
     * - Custom risk factors
     * 
     * Score ranges from 0.0 (no risk) to 1.0 (maximum risk).
     * 
     * @param projectId the project identifier
     * @return the risk score (0.0 - 1.0)
     */
    double calculateRiskScore(String projectId);
    
    /**
     * Initializes state for a new project.
     * 
     * Creates a new SDLC state entry with default values (PLANNING phase, LOW risk).
     * 
     * @param projectId the project identifier
     * @return the initialized state
     */
    SDLCState initializeProject(String projectId);
    
    /**
     * Adds a custom metric to a project's state.
     * 
     * Custom metrics can be used to track project-specific data that affects
     * risk or readiness calculations.
     * 
     * @param projectId the project identifier
     * @param key the metric key
     * @param value the metric value
     */
    void addCustomMetric(String projectId, String key, Object value);
    
    /**
     * Gets all custom metrics for a project.
     * 
     * @param projectId the project identifier
     * @return map of custom metrics
     */
    Map<String, Object> getCustomMetrics(String projectId);
}
