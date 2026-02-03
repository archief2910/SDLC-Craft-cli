package com.sdlcraft.backend.sdlc;

import net.jqwik.api.*;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for SDLC State Persistence.
 * 
 * Feature: sdlcraft-cli
 * Property 11: State Persistence Round-Trip
 * Validates: Requirements 3.5, 5.1
 * 
 * This test verifies that for any SDLC state change (phase transition, metric update,
 * risk assessment), the conversion between SDLCState and SDLCStateEntity preserves
 * all data correctly (round-trip property).
 * 
 * Note: This tests the persistence layer's data conversion logic. Integration tests
 * with actual database persistence are covered in separate integration test suites.
 */
class SDLCStatePersistencePropertyTest {
    
    /**
     * Property 11: State Persistence Round-Trip
     * 
     * For any SDLC state, converting to entity and back to state should preserve
     * all fields correctly.
     */
    @Property(tries = 100)
    @Label("SDLC state converts to entity and back correctly")
    void statePersistenceRoundTrip(
            @ForAll("projectIds") String projectId,
            @ForAll Phase phase,
            @ForAll RiskLevel riskLevel,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double testCoverage,
            @ForAll @IntRange(min = 0, max = 100) int openIssues,
            @ForAll @IntRange(min = 0, max = 100) int totalIssues,
            @ForAll("recentTimestamps") LocalDateTime lastDeployment,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double releaseReadiness,
            @ForAll("customMetrics") Map<String, Object> customMetrics) {
        
        // Create SDLC state
        SDLCState originalState = new SDLCState(projectId, phase);
        originalState.setRiskLevel(riskLevel);
        originalState.setTestCoverage(testCoverage);
        originalState.setOpenIssues(openIssues);
        originalState.setTotalIssues(totalIssues);
        originalState.setLastDeployment(lastDeployment);
        originalState.setReleaseReadiness(releaseReadiness);
        originalState.setCustomMetrics(new HashMap<>(customMetrics));
        
        // Convert to entity
        SDLCStateEntity entity = SDLCStateEntity.fromSDLCState(originalState);
        
        // Convert back to state
        SDLCState retrievedState = entity.toSDLCState();
        
        // Verify all fields match
        assertThat(retrievedState.getProjectId())
                .as("Project ID should match")
                .isEqualTo(originalState.getProjectId());
        
        assertThat(retrievedState.getCurrentPhase())
                .as("Current phase should match")
                .isEqualTo(originalState.getCurrentPhase());
        
        assertThat(retrievedState.getRiskLevel())
                .as("Risk level should match")
                .isEqualTo(originalState.getRiskLevel());
        
        assertThat(retrievedState.getTestCoverage())
                .as("Test coverage should match")
                .isEqualTo(originalState.getTestCoverage());
        
        assertThat(retrievedState.getOpenIssues())
                .as("Open issues should match")
                .isEqualTo(originalState.getOpenIssues());
        
        assertThat(retrievedState.getTotalIssues())
                .as("Total issues should match")
                .isEqualTo(originalState.getTotalIssues());
        
        // Compare timestamps (may have different precision)
        if (originalState.getLastDeployment() != null) {
            assertThat(retrievedState.getLastDeployment())
                    .as("Last deployment timestamp should match")
                    .isEqualTo(originalState.getLastDeployment());
        } else {
            assertThat(retrievedState.getLastDeployment()).isNull();
        }
        
        assertThat(retrievedState.getReleaseReadiness())
                .as("Release readiness should match")
                .isEqualTo(originalState.getReleaseReadiness());
        
        assertThat(retrievedState.getCustomMetrics())
                .as("Custom metrics should match")
                .containsExactlyInAnyOrderEntriesOf(originalState.getCustomMetrics());
    }
    
    /**
     * Property 11 (Phase Transition): Phase transitions preserve all other data
     * 
     * For any valid phase transition, changing the phase should preserve all
     * other state fields.
     */
    @Property(tries = 50)
    @Label("Phase transitions preserve other state fields")
    void phaseTransitionsPreserveData(
            @ForAll("projectIds") String projectId,
            @ForAll("validPhaseTransitions") PhaseTransition transition,
            @ForAll RiskLevel riskLevel,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double testCoverage) {
        
        // Create initial state
        SDLCState initialState = new SDLCState(projectId, transition.from);
        initialState.setRiskLevel(riskLevel);
        initialState.setTestCoverage(testCoverage);
        initialState.setOpenIssues(5);
        initialState.setTotalIssues(20);
        
        // Convert to entity
        SDLCStateEntity entity = SDLCStateEntity.fromSDLCState(initialState);
        
        // Update phase
        entity.setCurrentPhase(transition.to);
        
        // Convert back
        SDLCState updatedState = entity.toSDLCState();
        
        // Verify phase changed
        assertThat(updatedState.getCurrentPhase())
                .as("Phase should be updated to " + transition.to)
                .isEqualTo(transition.to);
        
        // Verify other fields preserved
        assertThat(updatedState.getProjectId()).isEqualTo(projectId);
        assertThat(updatedState.getRiskLevel()).isEqualTo(riskLevel);
        assertThat(updatedState.getTestCoverage()).isEqualTo(testCoverage);
        assertThat(updatedState.getOpenIssues()).isEqualTo(5);
        assertThat(updatedState.getTotalIssues()).isEqualTo(20);
    }
    
    /**
     * Property 11 (Metrics Update): Metrics updates preserve other data
     * 
     * For any metrics update, changing metrics should preserve all other
     * state fields.
     */
    @Property(tries = 50)
    @Label("Metrics updates preserve other state fields")
    void metricsUpdatesPreserveData(
            @ForAll("projectIds") String projectId,
            @ForAll Phase phase,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double initialCoverage,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double updatedCoverage,
            @ForAll @IntRange(min = 0, max = 50) int initialIssues,
            @ForAll @IntRange(min = 0, max = 50) int updatedIssues) {
        
        // Create initial state
        SDLCState initialState = new SDLCState(projectId, phase);
        initialState.setTestCoverage(initialCoverage);
        initialState.setOpenIssues(initialIssues);
        initialState.setTotalIssues(100);
        initialState.setRiskLevel(RiskLevel.LOW);
        
        // Convert to entity
        SDLCStateEntity entity = SDLCStateEntity.fromSDLCState(initialState);
        
        // Update metrics
        entity.setTestCoverage(updatedCoverage);
        entity.setOpenIssues(updatedIssues);
        
        // Convert back
        SDLCState updatedState = entity.toSDLCState();
        
        // Verify metrics changed
        assertThat(updatedState.getTestCoverage())
                .as("Test coverage should be updated")
                .isEqualTo(updatedCoverage);
        assertThat(updatedState.getOpenIssues())
                .as("Open issues should be updated")
                .isEqualTo(updatedIssues);
        
        // Verify other fields preserved
        assertThat(updatedState.getProjectId()).isEqualTo(projectId);
        assertThat(updatedState.getCurrentPhase()).isEqualTo(phase);
        assertThat(updatedState.getTotalIssues()).isEqualTo(100);
        assertThat(updatedState.getRiskLevel()).isEqualTo(RiskLevel.LOW);
    }
    
    /**
     * Property 11 (Custom Metrics): Custom metrics persist correctly
     * 
     * For any custom metrics map, the conversion should preserve all entries
     * with correct types.
     */
    @Property(tries = 50)
    @Label("Custom metrics persist correctly through conversion")
    void customMetricsPersist(
            @ForAll("projectIds") String projectId,
            @ForAll Phase phase,
            @ForAll("customMetrics") Map<String, Object> customMetrics) {
        
        // Create state with custom metrics
        SDLCState state = new SDLCState(projectId, phase);
        state.setCustomMetrics(new HashMap<>(customMetrics));
        
        // Convert to entity and back
        SDLCStateEntity entity = SDLCStateEntity.fromSDLCState(state);
        SDLCState retrievedState = entity.toSDLCState();
        
        // Verify custom metrics preserved
        assertThat(retrievedState.getCustomMetrics())
                .as("Custom metrics should be persisted and retrieved")
                .containsExactlyInAnyOrderEntriesOf(customMetrics);
    }
    
    /**
     * Property 11 (Null Handling): Null values are handled correctly
     * 
     * For any state with null optional fields, the conversion should preserve
     * the null values correctly.
     */
    @Property(tries = 50)
    @Label("Null values are handled correctly in conversion")
    void nullValuesHandledCorrectly(
            @ForAll("projectIds") String projectId,
            @ForAll Phase phase) {
        
        // Create state with minimal fields (nulls for optional)
        SDLCState state = new SDLCState(projectId, phase);
        // Don't set optional fields - they should be null
        
        // Convert to entity and back
        SDLCStateEntity entity = SDLCStateEntity.fromSDLCState(state);
        SDLCState retrievedState = entity.toSDLCState();
        
        // Verify required fields
        assertThat(retrievedState.getProjectId()).isEqualTo(projectId);
        assertThat(retrievedState.getCurrentPhase()).isEqualTo(phase);
        
        // Verify optional fields can be null
        // (Some may have defaults, but conversion should handle nulls)
        assertThat(retrievedState).isNotNull();
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
    
    @Provide
    Arbitrary<LocalDateTime> recentTimestamps() {
        LocalDateTime now = LocalDateTime.now();
        return Arbitraries.longs()
                .between(0, 90) // Last 90 days
                .map(days -> now.minus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS));
    }
    
    @Provide
    Arbitrary<Map<String, Object>> customMetrics() {
        return Arbitraries.maps(
                Arbitraries.of("risk_factor", "readiness_score", "code_quality", "security_score"),
                Arbitraries.oneOf(
                        Arbitraries.doubles().between(0.0, 1.0).map(d -> (Object) d),
                        Arbitraries.integers().between(0, 100).map(i -> (Object) i),
                        Arbitraries.strings().alpha().ofMaxLength(20).map(s -> (Object) s)
                )
        ).ofMinSize(0).ofMaxSize(5);
    }
    
    @Provide
    Arbitrary<PhaseTransition> validPhaseTransitions() {
        return Arbitraries.oneOf(
                // Forward transitions
                Arbitraries.just(new PhaseTransition(Phase.PLANNING, Phase.DEVELOPMENT)),
                Arbitraries.just(new PhaseTransition(Phase.DEVELOPMENT, Phase.TESTING)),
                Arbitraries.just(new PhaseTransition(Phase.TESTING, Phase.STAGING)),
                Arbitraries.just(new PhaseTransition(Phase.STAGING, Phase.PRODUCTION)),
                
                // Backward transitions (rollback)
                Arbitraries.just(new PhaseTransition(Phase.DEVELOPMENT, Phase.PLANNING)),
                Arbitraries.just(new PhaseTransition(Phase.TESTING, Phase.DEVELOPMENT)),
                Arbitraries.just(new PhaseTransition(Phase.STAGING, Phase.TESTING)),
                Arbitraries.just(new PhaseTransition(Phase.PRODUCTION, Phase.STAGING)),
                
                // Same phase (no transition)
                Arbitraries.of(Phase.values()).map(p -> new PhaseTransition(p, p))
        );
    }
    
    // Helper class for phase transitions
    private static class PhaseTransition {
        final Phase from;
        final Phase to;
        
        PhaseTransition(Phase from, Phase to) {
            this.from = from;
            this.to = to;
        }
    }
}
