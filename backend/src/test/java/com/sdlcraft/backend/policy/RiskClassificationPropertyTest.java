package com.sdlcraft.backend.policy;

import com.sdlcraft.backend.intent.IntentResult;
import com.sdlcraft.backend.sdlc.Phase;
import com.sdlcraft.backend.sdlc.RiskLevel;
import com.sdlcraft.backend.sdlc.SDLCState;
import net.jqwik.api.*;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.BeforeTry;

import java.time.LocalDateTime;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for Risk Classification.
 * 
 * Feature: sdlcraft-cli
 * Property 22: Risk Classification Correctness
 * Validates: Requirements 6.1
 * 
 * This test verifies that for any command, the Policy Engine classifies it as:
 * - LOW risk: Read operations, analysis, status checks
 * - MEDIUM risk: Staging operations, bulk updates, schema changes
 * - HIGH risk: Production operations, delete operations, reset operations
 * 
 * According to the design document risk classification rules.
 */
class RiskClassificationPropertyTest {
    
    private PolicyEngine policyEngine;
    
    @BeforeTry
    void setUp() {
        policyEngine = new DefaultPolicyEngine();
    }
    
    /**
     * Property 22: Production operations are classified as HIGH risk
     * 
     * For any command targeting production, the risk level should be HIGH.
     */
    @Property(tries = 100)
    @Label("Production operations are classified as HIGH risk")
    void productionOperationsAreHighRisk(
            @ForAll("validIntents") String intent,
            @ForAll Phase phase,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double testCoverage) {
        
        // Create intent targeting production
        IntentResult intentResult = createIntentResult(intent, "production");
        
        // Create SDLC state
        SDLCState state = createSDLCState(phase, testCoverage);
        
        // Assess risk
        RiskAssessment assessment = policyEngine.assessRisk(intentResult, state);
        
        // Verify production operations are HIGH risk
        assertThat(assessment.getLevel())
                .as("Production operations should be classified as HIGH risk")
                .isEqualTo(RiskLevel.HIGH);
        
        // Verify confirmation is required
        assertThat(assessment.isRequiresConfirmation())
                .as("Production operations should require confirmation")
                .isTrue();
    }
    
    /**
     * Property 22: Destructive operations are classified as HIGH risk
     * 
     * For any command with destructive intent (delete, reset, destroy, remove),
     * the risk level should be HIGH.
     */
    @Property(tries = 100)
    @Label("Destructive operations are classified as HIGH risk")
    void destructiveOperationsAreHighRisk(
            @ForAll("destructiveIntents") String destructiveIntent,
            @ForAll("nonProductionTargets") String target,
            @ForAll Phase phase) {
        
        // Create destructive intent
        IntentResult intentResult = createIntentResult(destructiveIntent, target);
        
        // Create SDLC state
        SDLCState state = createSDLCState(phase, 0.8);
        
        // Assess risk
        RiskAssessment assessment = policyEngine.assessRisk(intentResult, state);
        
        // Verify destructive operations are HIGH risk
        assertThat(assessment.getLevel())
                .as("Destructive operations should be classified as HIGH risk")
                .isEqualTo(RiskLevel.HIGH);
        
        // Verify confirmation is required
        assertThat(assessment.isRequiresConfirmation())
                .as("Destructive operations should require confirmation")
                .isTrue();
    }
    
    /**
     * Property 22: Release intent is classified as HIGH risk
     * 
     * For any release command, the risk level should be HIGH.
     */
    @Property(tries = 100)
    @Label("Release intent is classified as HIGH risk")
    void releaseIntentIsHighRisk(
            @ForAll("releaseTargets") String target,
            @ForAll Phase phase,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double testCoverage) {
        
        // Create release intent
        IntentResult intentResult = createIntentResult("release", target);
        
        // Create SDLC state
        SDLCState state = createSDLCState(phase, testCoverage);
        
        // Assess risk
        RiskAssessment assessment = policyEngine.assessRisk(intentResult, state);
        
        // Verify release is HIGH risk
        assertThat(assessment.getLevel())
                .as("Release intent should be classified as HIGH risk")
                .isEqualTo(RiskLevel.HIGH);
    }
    
    /**
     * Property 22: Staging operations are classified as MEDIUM risk
     * 
     * For any command targeting staging (not destructive), the risk level should be MEDIUM.
     */
    @Property(tries = 100)
    @Label("Staging operations are classified as MEDIUM risk")
    void stagingOperationsAreMediumRisk(
            @ForAll("nonDestructiveIntents") String intent,
            @ForAll Phase phase,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double testCoverage) {
        
        // Create intent targeting staging
        IntentResult intentResult = createIntentResult(intent, "staging");
        
        // Create SDLC state with low risk
        SDLCState state = createSDLCState(phase, testCoverage);
        state.setRiskLevel(RiskLevel.LOW);
        
        // Assess risk
        RiskAssessment assessment = policyEngine.assessRisk(intentResult, state);
        
        // Verify staging operations are MEDIUM risk
        assertThat(assessment.getLevel())
                .as("Staging operations should be classified as MEDIUM risk")
                .isEqualTo(RiskLevel.MEDIUM);
    }
    
    /**
     * Property 22: Improve and prepare intents are classified as MEDIUM risk
     * 
     * For any improve or prepare command, the risk level should be MEDIUM.
     */
    @Property(tries = 100)
    @Label("Improve and prepare intents are classified as MEDIUM risk")
    void improveAndPrepareAreMediumRisk(
            @ForAll("improvePrepareIntents") String intent,
            @ForAll("nonProductionTargets") String target,
            @ForAll Phase phase) {
        
        // Create improve/prepare intent
        IntentResult intentResult = createIntentResult(intent, target);
        
        // Create SDLC state with low risk
        SDLCState state = createSDLCState(phase, 0.8);
        state.setRiskLevel(RiskLevel.LOW);
        
        // Assess risk
        RiskAssessment assessment = policyEngine.assessRisk(intentResult, state);
        
        // Verify improve/prepare are MEDIUM risk
        assertThat(assessment.getLevel())
                .as("Improve and prepare intents should be classified as MEDIUM risk")
                .isEqualTo(RiskLevel.MEDIUM);
    }
    
    /**
     * Property 22: Read operations are classified as LOW risk
     * 
     * For any read-only command (status, analyze without production/staging target),
     * the risk level should be LOW.
     */
    @Property(tries = 100)
    @Label("Read operations are classified as LOW risk")
    void readOperationsAreLowRisk(
            @ForAll("readOnlyIntents") String intent,
            @ForAll("lowRiskTargets") String target,
            @ForAll Phase phase) {
        
        // Create read-only intent
        IntentResult intentResult = createIntentResult(intent, target);
        
        // Create SDLC state with low risk
        SDLCState state = createSDLCState(phase, 0.8);
        state.setRiskLevel(RiskLevel.LOW);
        
        // Assess risk
        RiskAssessment assessment = policyEngine.assessRisk(intentResult, state);
        
        // Verify read operations are LOW risk
        assertThat(assessment.getLevel())
                .as("Read operations should be classified as LOW risk")
                .isEqualTo(RiskLevel.LOW);
    }
    
    /**
     * Property 22: High-risk state elevates command risk
     * 
     * For any non-production command, if the project state has HIGH or CRITICAL risk,
     * the command risk should be elevated to at least MEDIUM.
     */
    @Property(tries = 100)
    @Label("High-risk state elevates command risk to at least MEDIUM")
    void highRiskStateElevatesCommandRisk(
            @ForAll("readOnlyIntents") String intent,
            @ForAll("nonProductionTargets") String target,
            @ForAll Phase phase,
            @ForAll("highRiskLevels") RiskLevel stateRiskLevel) {
        
        // Create read-only intent (normally LOW risk)
        IntentResult intentResult = createIntentResult(intent, target);
        
        // Create SDLC state with high risk
        SDLCState state = createSDLCState(phase, 0.4);
        state.setRiskLevel(stateRiskLevel);
        
        // Assess risk
        RiskAssessment assessment = policyEngine.assessRisk(intentResult, state);
        
        // Verify risk is elevated to at least MEDIUM
        assertThat(assessment.getLevel())
                .as("High-risk state should elevate command risk to at least MEDIUM")
                .isIn(RiskLevel.MEDIUM, RiskLevel.HIGH, RiskLevel.CRITICAL);
    }
    
    /**
     * Property 22: Risk assessment includes explanation
     * 
     * For any command, the risk assessment should include a non-empty explanation.
     */
    @Property(tries = 100)
    @Label("Risk assessment includes explanation")
    void riskAssessmentIncludesExplanation(
            @ForAll("validIntents") String intent,
            @ForAll("allTargets") String target,
            @ForAll Phase phase,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double testCoverage) {
        
        // Create intent
        IntentResult intentResult = createIntentResult(intent, target);
        
        // Create SDLC state
        SDLCState state = createSDLCState(phase, testCoverage);
        
        // Assess risk
        RiskAssessment assessment = policyEngine.assessRisk(intentResult, state);
        
        // Verify explanation is present
        assertThat(assessment.getExplanation())
                .as("Risk assessment should include explanation")
                .isNotNull()
                .isNotBlank();
    }
    
    /**
     * Property 22: Risk assessment includes impact description
     * 
     * For any command, the risk assessment should include an impact description.
     */
    @Property(tries = 100)
    @Label("Risk assessment includes impact description")
    void riskAssessmentIncludesImpactDescription(
            @ForAll("validIntents") String intent,
            @ForAll("allTargets") String target,
            @ForAll Phase phase) {
        
        // Create intent
        IntentResult intentResult = createIntentResult(intent, target);
        
        // Create SDLC state
        SDLCState state = createSDLCState(phase, 0.8);
        
        // Assess risk
        RiskAssessment assessment = policyEngine.assessRisk(intentResult, state);
        
        // Verify impact description is present
        assertThat(assessment.getImpactDescription())
                .as("Risk assessment should include impact description")
                .isNotNull()
                .isNotBlank();
    }
    
    /**
     * Property 22: Confirmation requirement matches risk level
     * 
     * For any command, confirmation should be required if:
     * - Risk level is HIGH or CRITICAL
     * - Target is production
     * - Intent is destructive
     */
    @Property(tries = 100)
    @Label("Confirmation requirement matches risk level and operation type")
    void confirmationRequirementMatchesRisk(
            @ForAll("validIntents") String intent,
            @ForAll("allTargets") String target,
            @ForAll Phase phase) {
        
        // Create intent
        IntentResult intentResult = createIntentResult(intent, target);
        
        // Create SDLC state
        SDLCState state = createSDLCState(phase, 0.8);
        
        // Assess risk
        RiskAssessment assessment = policyEngine.assessRisk(intentResult, state);
        
        boolean isProductionOp = "production".equalsIgnoreCase(target);
        boolean isDestructiveOp = intent.toLowerCase().contains("delete") ||
                                  intent.toLowerCase().contains("reset") ||
                                  intent.toLowerCase().contains("destroy") ||
                                  intent.toLowerCase().contains("remove");
        boolean isHighRisk = assessment.getLevel().isHigherThan(RiskLevel.MEDIUM);
        
        boolean shouldRequireConfirmation = isHighRisk || isProductionOp || isDestructiveOp;
        
        // Verify confirmation requirement
        assertThat(assessment.isRequiresConfirmation())
                .as("Confirmation requirement should match risk level and operation type")
                .isEqualTo(shouldRequireConfirmation);
    }
    
    /**
     * Property 22: Production operations add production concern
     * 
     * For any production operation, the concerns list should include
     * a mention of production environment modification.
     */
    @Property(tries = 100)
    @Label("Production operations add production concern")
    void productionOperationsAddProductionConcern(
            @ForAll("validIntents") String intent,
            @ForAll Phase phase) {
        
        // Create production intent
        IntentResult intentResult = createIntentResult(intent, "production");
        
        // Create SDLC state
        SDLCState state = createSDLCState(phase, 0.8);
        
        // Assess risk
        RiskAssessment assessment = policyEngine.assessRisk(intentResult, state);
        
        // Verify production concern is present
        assertThat(assessment.getConcerns())
                .as("Production operations should include production concern")
                .anyMatch(concern -> concern.toLowerCase().contains("production"));
    }
    
    /**
     * Property 22: Low test coverage adds concern
     * 
     * For any command with test coverage < 70%, the concerns list should
     * include a mention of low test coverage.
     */
    @Property(tries = 100)
    @Label("Low test coverage adds concern")
    void lowTestCoverageAddsConcern(
            @ForAll("validIntents") String intent,
            @ForAll("allTargets") String target,
            @ForAll Phase phase,
            @ForAll @DoubleRange(min = 0.0, max = 0.69) double lowCoverage) {
        
        // Create intent
        IntentResult intentResult = createIntentResult(intent, target);
        
        // Create SDLC state with low coverage
        SDLCState state = createSDLCState(phase, lowCoverage);
        
        // Assess risk
        RiskAssessment assessment = policyEngine.assessRisk(intentResult, state);
        
        // Verify coverage concern is present
        assertThat(assessment.getConcerns())
                .as("Low test coverage should add concern")
                .anyMatch(concern -> concern.toLowerCase().contains("coverage"));
    }
    
    // Helper methods
    
    private IntentResult createIntentResult(String intent, String target) {
        IntentResult result = new IntentResult(intent, target, 0.9, "Test intent");
        result.setModifiers(new HashMap<>());
        return result;
    }
    
    private SDLCState createSDLCState(Phase phase, double testCoverage) {
        SDLCState state = new SDLCState("test-project", phase);
        state.setTestCoverage(testCoverage);
        state.setOpenIssues(5);
        state.setTotalIssues(20);
        state.setLastDeployment(LocalDateTime.now().minusDays(7));
        state.setRiskLevel(RiskLevel.LOW);
        return state;
    }
    
    // Arbitraries (data generators)
    
    @Provide
    Arbitrary<String> validIntents() {
        return Arbitraries.of("status", "analyze", "improve", "test", "debug", "prepare", "release");
    }
    
    @Provide
    Arbitrary<String> destructiveIntents() {
        return Arbitraries.of("delete", "reset", "destroy", "remove", 
                             "delete-all", "reset-database", "destroy-resources");
    }
    
    @Provide
    Arbitrary<String> nonDestructiveIntents() {
        return Arbitraries.of("status", "analyze", "test", "debug");
    }
    
    @Provide
    Arbitrary<String> improvePrepareIntents() {
        return Arbitraries.of("improve", "prepare");
    }
    
    @Provide
    Arbitrary<String> readOnlyIntents() {
        return Arbitraries.of("status", "analyze", "test");
    }
    
    @Provide
    Arbitrary<String> releaseTargets() {
        return Arbitraries.of("staging", "production", "development");
    }
    
    @Provide
    Arbitrary<String> nonProductionTargets() {
        return Arbitraries.of("staging", "development", "security", "performance", "quality");
    }
    
    @Provide
    Arbitrary<String> lowRiskTargets() {
        return Arbitraries.of("development", "security", "performance", "quality", "reliability");
    }
    
    @Provide
    Arbitrary<String> allTargets() {
        return Arbitraries.of("production", "staging", "development", 
                             "security", "performance", "quality", "reliability");
    }
    
    @Provide
    Arbitrary<RiskLevel> highRiskLevels() {
        return Arbitraries.of(RiskLevel.HIGH, RiskLevel.CRITICAL);
    }
}
