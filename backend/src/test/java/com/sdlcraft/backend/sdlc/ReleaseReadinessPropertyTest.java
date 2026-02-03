package com.sdlcraft.backend.sdlc;

import net.jqwik.api.*;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for Release Readiness Calculation.
 * 
 * Feature: sdlcraft-cli
 * Property 12: Release Readiness Calculation
 * Validates: Requirements 3.4
 * 
 * This test verifies that for any project state with defined test coverage, open issues,
 * and deployment history, the SDLC State Machine calculates a release readiness score
 * using the defined formula:
 * 
 * Readiness = testCoverage * 0.4 + (1 - openIssues / totalIssues) * 0.3 +
 *             (1 - daysSinceLastDeploy / 30) * 0.2 + customReadinessFactors * 0.1
 * 
 * The score ranges from 0.0 (not ready) to 1.0 (fully ready).
 */
class ReleaseReadinessPropertyTest {
    
    private SDLCStateMachine stateMachine;
    private SDLCStateRepository mockRepository;
    
    // Weights from the formula
    private static final double COVERAGE_WEIGHT = 0.4;
    private static final double ISSUES_WEIGHT = 0.3;
    private static final double DEPLOYMENT_WEIGHT = 0.2;
    private static final double CUSTOM_WEIGHT = 0.1;
    private static final int MAX_DAYS_SINCE_DEPLOYMENT = 30;
    
    @BeforeTry
    void setUp() {
        mockRepository = Mockito.mock(SDLCStateRepository.class);
        stateMachine = new DefaultSDLCStateMachine(mockRepository);
    }
    
    /**
     * Property 12: Release Readiness Calculation
     * 
     * For any project state with defined metrics, the calculated readiness score
     * should match the formula: testCoverage * 0.4 + (1 - openIssues/totalIssues) * 0.3 +
     * (1 - daysSinceLastDeploy/30) * 0.2 + customReadinessFactors * 0.1
     */
    @Property(tries = 100)
    @Label("Release readiness score follows the defined formula")
    void releaseReadinessFollowsFormula(
            @ForAll("projectIds") String projectId,
            @ForAll Phase phase,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double testCoverage,
            @ForAll @IntRange(min = 0, max = 50) int openIssues,
            @ForAll @IntRange(min = 1, max = 100) int totalIssues,
            @ForAll @IntRange(min = 0, max = 60) int daysSinceDeployment) {
        
        // Ensure openIssues <= totalIssues
        int actualOpenIssues = Math.min(openIssues, totalIssues);
        
        // Create state entity with metrics
        SDLCStateEntity entity = createStateEntity(
                projectId, phase, testCoverage, actualOpenIssues, totalIssues, daysSinceDeployment);
        
        // Mock repository
        when(mockRepository.findById(projectId)).thenReturn(Optional.of(entity));
        
        // Calculate readiness
        ReleaseReadiness readiness = stateMachine.calculateReadiness(projectId);
        
        // Calculate expected score using the formula
        double expectedScore = calculateExpectedReadiness(
                testCoverage, actualOpenIssues, totalIssues, daysSinceDeployment, new HashMap<>());
        
        // Verify score matches formula (with small tolerance for floating point)
        assertThat(readiness.getScore())
                .as("Readiness score should match formula")
                .isCloseTo(expectedScore, Offset.offset(0.001));
        
        // Verify score is in valid range [0.0, 1.0]
        assertThat(readiness.getScore())
                .as("Readiness score should be between 0.0 and 1.0")
                .isBetween(0.0, 1.0);
    }
    
    /**
     * Property 12: Readiness increases with better metrics
     * 
     * For any project state, improving metrics (higher coverage, fewer issues,
     * more recent deployment) should increase or maintain readiness score.
     */
    @Property(tries = 100)
    @Label("Readiness increases with better metrics")
    void readinessIncreasesWithBetterMetrics(
            @ForAll("projectIds") String projectId,
            @ForAll Phase phase,
            @ForAll @DoubleRange(min = 0.0, max = 0.9) double initialCoverage,
            @ForAll @IntRange(min = 5, max = 50) int initialOpenIssues,
            @ForAll @IntRange(min = 10, max = 100) int totalIssues,
            @ForAll @IntRange(min = 10, max = 60) int initialDaysSince) {
        
        // Ensure valid constraints
        int actualInitialOpenIssues = Math.min(initialOpenIssues, totalIssues);
        double improvedCoverage = Math.min(1.0, initialCoverage + 0.1);
        int improvedOpenIssues = Math.max(0, actualInitialOpenIssues - 5);
        int improvedDaysSince = Math.max(0, initialDaysSince - 10);
        
        // Create initial state
        SDLCStateEntity initialEntity = createStateEntity(
                projectId, phase, initialCoverage, actualInitialOpenIssues, totalIssues, initialDaysSince);
        
        // Create improved state
        SDLCStateEntity improvedEntity = createStateEntity(
                projectId, phase, improvedCoverage, improvedOpenIssues, totalIssues, improvedDaysSince);
        
        // Mock repository for initial state
        when(mockRepository.findById(projectId)).thenReturn(Optional.of(initialEntity));
        ReleaseReadiness initialReadiness = stateMachine.calculateReadiness(projectId);
        
        // Mock repository for improved state
        when(mockRepository.findById(projectId)).thenReturn(Optional.of(improvedEntity));
        ReleaseReadiness improvedReadiness = stateMachine.calculateReadiness(projectId);
        
        // Verify improved readiness is higher or equal
        assertThat(improvedReadiness.getScore())
                .as("Improved metrics should result in higher or equal readiness")
                .isGreaterThanOrEqualTo(initialReadiness.getScore());
    }
    
    /**
     * Property 12: Readiness status matches score thresholds
     * 
     * For any readiness score, the status should match the defined thresholds:
     * - READY: score >= 0.9
     * - ALMOST_READY: score >= 0.7
     * - NOT_READY: score >= 0.5
     * - BLOCKED: score < 0.5
     */
    @Property(tries = 100)
    @Label("Readiness status matches score thresholds")
    void readinessStatusMatchesThresholds(
            @ForAll("projectIds") String projectId,
            @ForAll Phase phase,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double testCoverage,
            @ForAll @IntRange(min = 0, max = 50) int openIssues,
            @ForAll @IntRange(min = 1, max = 100) int totalIssues,
            @ForAll @IntRange(min = 0, max = 60) int daysSinceDeployment) {
        
        int actualOpenIssues = Math.min(openIssues, totalIssues);
        
        SDLCStateEntity entity = createStateEntity(
                projectId, phase, testCoverage, actualOpenIssues, totalIssues, daysSinceDeployment);
        
        when(mockRepository.findById(projectId)).thenReturn(Optional.of(entity));
        
        ReleaseReadiness readiness = stateMachine.calculateReadiness(projectId);
        double score = readiness.getScore();
        ReleaseReadiness.ReadinessStatus status = readiness.getStatus();
        
        // Verify status matches score thresholds
        if (score >= 0.9) {
            assertThat(status)
                    .as("Score >= 0.9 should be READY")
                    .isEqualTo(ReleaseReadiness.ReadinessStatus.READY);
        } else if (score >= 0.7) {
            assertThat(status)
                    .as("Score >= 0.7 should be ALMOST_READY")
                    .isEqualTo(ReleaseReadiness.ReadinessStatus.ALMOST_READY);
        } else if (score >= 0.5) {
            assertThat(status)
                    .as("Score >= 0.5 should be NOT_READY")
                    .isEqualTo(ReleaseReadiness.ReadinessStatus.NOT_READY);
        } else {
            assertThat(status)
                    .as("Score < 0.5 should be BLOCKED")
                    .isEqualTo(ReleaseReadiness.ReadinessStatus.BLOCKED);
        }
    }
    
    /**
     * Property 12: Custom readiness factors affect score
     * 
     * For any project with custom readiness factors, the score should incorporate
     * them with 10% weight.
     */
    @Property(tries = 100)
    @Label("Custom readiness factors affect score correctly")
    void customReadinessFactorsAffectScore(
            @ForAll("projectIds") String projectId,
            @ForAll Phase phase,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double testCoverage,
            @ForAll @IntRange(min = 0, max = 20) int openIssues,
            @ForAll @IntRange(min = 1, max = 100) int totalIssues,
            @ForAll @IntRange(min = 0, max = 30) int daysSinceDeployment,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double customFactor) {
        
        int actualOpenIssues = Math.min(openIssues, totalIssues);
        
        // Create state without custom factors
        SDLCStateEntity entityWithoutCustom = createStateEntity(
                projectId, phase, testCoverage, actualOpenIssues, totalIssues, daysSinceDeployment);
        
        // Create state with custom readiness factor
        SDLCStateEntity entityWithCustom = createStateEntity(
                projectId, phase, testCoverage, actualOpenIssues, totalIssues, daysSinceDeployment);
        Map<String, Object> customMetrics = new HashMap<>();
        customMetrics.put("readiness_custom", customFactor);
        entityWithCustom.setCustomMetrics(customMetrics);
        
        // Calculate readiness without custom factors
        when(mockRepository.findById(projectId)).thenReturn(Optional.of(entityWithoutCustom));
        ReleaseReadiness readinessWithoutCustom = stateMachine.calculateReadiness(projectId);
        
        // Calculate readiness with custom factors
        when(mockRepository.findById(projectId)).thenReturn(Optional.of(entityWithCustom));
        ReleaseReadiness readinessWithCustom = stateMachine.calculateReadiness(projectId);
        
        // Expected difference is customFactor * CUSTOM_WEIGHT
        double expectedDifference = customFactor * CUSTOM_WEIGHT;
        double actualDifference = readinessWithCustom.getScore() - readinessWithoutCustom.getScore();
        
        // Verify custom factor affects score correctly
        assertThat(actualDifference)
                .as("Custom readiness factor should affect score by factor * 0.1")
                .isCloseTo(expectedDifference, Offset.offset(0.001));
    }
    
    /**
     * Property 12: Perfect metrics yield high readiness
     * 
     * For any project with perfect metrics (100% coverage, 0 issues, very recent deployment),
     * the readiness score should be >= 0.85 (high readiness).
     */
    @Property(tries = 50)
    @Label("Perfect metrics yield high readiness score")
    void perfectMetricsYieldHighReadiness(
            @ForAll("projectIds") String projectId,
            @ForAll Phase phase) {
        
        // Perfect metrics with 0 days since deployment
        double testCoverage = 1.0;
        int openIssues = 0;
        int totalIssues = 100;
        int daysSinceDeployment = 0;
        
        SDLCStateEntity entity = createStateEntity(
                projectId, phase, testCoverage, openIssues, totalIssues, daysSinceDeployment);
        
        when(mockRepository.findById(projectId)).thenReturn(Optional.of(entity));
        
        ReleaseReadiness readiness = stateMachine.calculateReadiness(projectId);
        
        // With perfect metrics and 0 days since deployment, readiness should be 0.9
        // (0.4 coverage + 0.3 issues + 0.2 deployment + 0 custom = 0.9)
        assertThat(readiness.getScore())
                .as("Perfect metrics should yield readiness close to 0.9")
                .isCloseTo(0.9, Offset.offset(0.01)); // Allow small tolerance for floating point
        
        assertThat(readiness.getStatus())
                .as("Perfect metrics should yield READY or ALMOST_READY status")
                .isIn(ReleaseReadiness.ReadinessStatus.READY, ReleaseReadiness.ReadinessStatus.ALMOST_READY);
    }
    
    /**
     * Property 12: Poor metrics yield low readiness
     * 
     * For any project with poor metrics (low coverage, many issues, old deployment),
     * the readiness score should be < 0.5 (BLOCKED status).
     */
    @Property(tries = 50)
    @Label("Poor metrics yield low readiness score")
    void poorMetricsYieldLowReadiness(
            @ForAll("projectIds") String projectId,
            @ForAll Phase phase,
            @ForAll @DoubleRange(min = 0.0, max = 0.3) double testCoverage,
            @ForAll @IntRange(min = 40, max = 100) int totalIssues,
            @ForAll @IntRange(min = 45, max = 90) int daysSinceDeployment) {
        
        // Poor metrics
        int openIssues = (int) (totalIssues * 0.8); // 80% of issues are open
        
        SDLCStateEntity entity = createStateEntity(
                projectId, phase, testCoverage, openIssues, totalIssues, daysSinceDeployment);
        
        when(mockRepository.findById(projectId)).thenReturn(Optional.of(entity));
        
        ReleaseReadiness readiness = stateMachine.calculateReadiness(projectId);
        
        // With poor metrics, readiness should be low
        assertThat(readiness.getScore())
                .as("Poor metrics should yield readiness < 0.5")
                .isLessThan(0.5);
        
        assertThat(readiness.getStatus())
                .as("Poor metrics should yield BLOCKED status")
                .isEqualTo(ReleaseReadiness.ReadinessStatus.BLOCKED);
    }
    
    /**
     * Property 12: Readiness factors are populated
     * 
     * For any project state, the readiness assessment should include
     * ready factors and/or blocking factors explaining the score.
     */
    @Property(tries = 100)
    @Label("Readiness assessment includes explanatory factors")
    void readinessIncludesFactors(
            @ForAll("projectIds") String projectId,
            @ForAll Phase phase,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double testCoverage,
            @ForAll @IntRange(min = 0, max = 50) int openIssues,
            @ForAll @IntRange(min = 1, max = 100) int totalIssues,
            @ForAll @IntRange(min = 0, max = 60) int daysSinceDeployment) {
        
        int actualOpenIssues = Math.min(openIssues, totalIssues);
        
        SDLCStateEntity entity = createStateEntity(
                projectId, phase, testCoverage, actualOpenIssues, totalIssues, daysSinceDeployment);
        
        when(mockRepository.findById(projectId)).thenReturn(Optional.of(entity));
        
        ReleaseReadiness readiness = stateMachine.calculateReadiness(projectId);
        
        // Verify that factors are provided
        int totalFactors = readiness.getReadyFactors().size() + readiness.getBlockingFactors().size();
        
        assertThat(totalFactors)
                .as("Readiness assessment should include explanatory factors")
                .isGreaterThan(0);
    }
    
    // Helper methods
    
    private SDLCStateEntity createStateEntity(
            String projectId, Phase phase, double testCoverage,
            int openIssues, int totalIssues, int daysSinceDeployment) {
        
        SDLCStateEntity entity = new SDLCStateEntity(projectId, phase);
        entity.setTestCoverage(testCoverage);
        entity.setOpenIssues(openIssues);
        entity.setTotalIssues(totalIssues);
        entity.setLastDeployment(LocalDateTime.now().minus(daysSinceDeployment, ChronoUnit.DAYS));
        entity.setCustomMetrics(new HashMap<>());
        
        return entity;
    }
    
    private double calculateExpectedReadiness(
            double testCoverage, int openIssues, int totalIssues,
            int daysSinceDeployment, Map<String, Object> customMetrics) {
        
        // Factor 1: Test coverage
        double coverageReadiness = testCoverage * COVERAGE_WEIGHT;
        
        // Factor 2: Open issues
        double issuesReadiness = 0.0;
        if (totalIssues > 0) {
            issuesReadiness = (1.0 - ((double) openIssues / totalIssues)) * ISSUES_WEIGHT;
        } else if (openIssues == 0) {
            issuesReadiness = ISSUES_WEIGHT;
        }
        
        // Factor 3: Deployment recency
        double deploymentReadiness = (1.0 - Math.min(1.0, (double) daysSinceDeployment / MAX_DAYS_SINCE_DEPLOYMENT)) * DEPLOYMENT_WEIGHT;
        
        // Factor 4: Custom readiness factors
        double customReadiness = calculateCustomReadinessFactor(customMetrics) * CUSTOM_WEIGHT;
        
        double readiness = coverageReadiness + issuesReadiness + deploymentReadiness + customReadiness;
        
        // Clamp to [0.0, 1.0]
        return Math.max(0.0, Math.min(1.0, readiness));
    }
    
    private double calculateCustomReadinessFactor(Map<String, Object> customMetrics) {
        double customReadiness = 0.0;
        int count = 0;
        
        for (Map.Entry<String, Object> entry : customMetrics.entrySet()) {
            if (entry.getKey().startsWith("readiness_") && entry.getValue() instanceof Number) {
                customReadiness += ((Number) entry.getValue()).doubleValue();
                count++;
            }
        }
        
        return count > 0 ? customReadiness / count : 0.0;
    }
    
    // Arbitraries (data generators)
    
    @Provide
    Arbitrary<String> projectIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(5)
                .ofMaxLength(20)
                .map(s -> "project-" + s);
    }
    
    // Helper class for offset assertions
    private static class Offset {
        private final double value;
        
        private Offset(double value) {
            this.value = value;
        }
        
        static org.assertj.core.data.Offset<Double> offset(double value) {
            return org.assertj.core.data.Offset.offset(value);
        }
    }
}
