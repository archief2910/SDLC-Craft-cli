package com.sdlcraft.backend.handler;

import com.sdlcraft.backend.agent.AgentContext;
import com.sdlcraft.backend.agent.AgentPhase;
import com.sdlcraft.backend.agent.AgentResult;
import com.sdlcraft.backend.agent.AgentStatus;
import com.sdlcraft.backend.intent.IntentResult;
import com.sdlcraft.backend.memory.ExecutionStatistics;
import com.sdlcraft.backend.memory.LongTermMemory;
import com.sdlcraft.backend.sdlc.Metrics;
import com.sdlcraft.backend.sdlc.ReleaseReadiness;
import com.sdlcraft.backend.sdlc.SDLCState;
import com.sdlcraft.backend.sdlc.SDLCStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Handler for "status" intent.
 * 
 * Queries current SDLC state and returns comprehensive project status including:
 * - Current phase
 * - Risk level
 * - Test coverage
 * - Release readiness
 * - Recent activity
 * - Execution statistics
 * 
 * Design rationale:
 * - Aggregates data from multiple sources
 * - Provides both summary and detailed views
 * - Supports verbose mode for detailed metrics
 * - Formats output for human readability
 */
@Component
public class StatusIntentHandler implements IntentHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(StatusIntentHandler.class);
    
    private static final String INTENT_NAME = "status";
    
    private final SDLCStateMachine stateMachine;
    private final LongTermMemory memory;
    
    public StatusIntentHandler(SDLCStateMachine stateMachine, LongTermMemory memory) {
        this.stateMachine = stateMachine;
        this.memory = memory;
    }
    
    @Override
    public String getIntentName() {
        return INTENT_NAME;
    }
    
    @Override
    public boolean canHandle(IntentResult intent, SDLCState state) {
        return INTENT_NAME.equals(intent.getIntent());
    }
    
    @Override
    public AgentResult handle(AgentContext context) {
        logger.info("Handling status intent for project: {}", context.getProjectId());
        
        try {
            String projectId = context.getProjectId();
            
            // Get current SDLC state
            SDLCState state = stateMachine.getCurrentState(projectId);
            
            // Calculate release readiness
            ReleaseReadiness readiness = stateMachine.calculateReadiness(projectId);
            
            // Get execution statistics
            ExecutionStatistics stats = memory.getStatistics(projectId);
            
            // Check if verbose mode requested
            boolean verbose = context.getParameter("verbose") != null;
            
            // Build status response
            Map<String, Object> statusData = new HashMap<>();
            statusData.put("projectId", projectId);
            statusData.put("currentPhase", state.getCurrentPhase().toString());
            statusData.put("riskLevel", state.getRiskLevel().toString());
            statusData.put("testCoverage", formatPercentage(state.getTestCoverage()));
            statusData.put("openIssues", state.getOpenIssues());
            statusData.put("releaseReadiness", formatPercentage(readiness.getScore()));
            statusData.put("readinessStatus", readiness.getStatus().toString());
            
            if (state.getLastDeployment() != null) {
                statusData.put("lastDeployment", formatDateTime(state.getLastDeployment()));
            }
            
            // Add statistics
            Map<String, Object> statisticsData = new HashMap<>();
            statisticsData.put("totalExecutions", stats.getTotalExecutions());
            statisticsData.put("successRate", formatPercentage(stats.getSuccessRate()));
            statisticsData.put("averageDuration", formatDuration(stats.getAverageDurationMs()));
            statisticsData.put("mostCommonIntent", stats.getMostCommonIntent());
            statusData.put("statistics", statisticsData);
            
            // Add verbose details if requested
            if (verbose) {
                statusData.put("customMetrics", state.getCustomMetrics());
                statusData.put("readinessFactors", readiness.getReadyFactors());
                statusData.put("blockers", readiness.getBlockingFactors());
            }
            
            // Build human-readable summary
            String summary = buildStatusSummary(state, readiness, stats);
            
            // Build reasoning
            String reasoning = buildStatusReasoning(state, readiness, verbose);
            
            return AgentResult.builder()
                    .agentType("status-handler")
                    .phase(AgentPhase.ACT)
                    .status(AgentStatus.SUCCESS)
                    .data("status", statusData)
                    .data("summary", summary)
                    .reasoning(reasoning)
                    .build();
                    
        } catch (Exception e) {
            logger.error("Failed to handle status intent", e);
            return AgentResult.builder()
                    .agentType("status-handler")
                    .phase(AgentPhase.ACT)
                    .status(AgentStatus.FAILURE)
                    .error("Failed to retrieve status: " + e.getMessage())
                    .reasoning("Exception occurred while querying SDLC state")
                    .build();
        }
    }
    
    @Override
    public String getHelpText() {
        return "Display current SDLC state including phase, risk level, test coverage, " +
               "and release readiness. Use --verbose for detailed metrics.";
    }
    
    /**
     * Build human-readable status summary.
     */
    private String buildStatusSummary(SDLCState state, ReleaseReadiness readiness,
                                     ExecutionStatistics stats) {
        StringBuilder summary = new StringBuilder();
        
        summary.append("Project Status:\n");
        summary.append("  Phase: ").append(state.getCurrentPhase()).append("\n");
        summary.append("  Risk Level: ").append(state.getRiskLevel()).append("\n");
        summary.append("  Test Coverage: ").append(formatPercentage(state.getTestCoverage())).append("\n");
        summary.append("  Open Issues: ").append(state.getOpenIssues()).append("\n");
        summary.append("  Release Readiness: ").append(formatPercentage(readiness.getScore()))
                .append(" (").append(readiness.getStatus()).append(")\n");
        
        if (state.getLastDeployment() != null) {
            summary.append("  Last Deployment: ").append(formatDateTime(state.getLastDeployment())).append("\n");
        }
        
        summary.append("\nRecent Activity:\n");
        summary.append("  Total Commands: ").append(stats.getTotalExecutions()).append("\n");
        summary.append("  Success Rate: ").append(formatPercentage(stats.getSuccessRate())).append("\n");
        summary.append("  Most Common: ").append(stats.getMostCommonIntent()).append("\n");
        
        return summary.toString();
    }
    
    /**
     * Build reasoning explanation.
     */
    private String buildStatusReasoning(SDLCState state, ReleaseReadiness readiness, boolean verbose) {
        StringBuilder reasoning = new StringBuilder();
        
        reasoning.append("Retrieved current SDLC state for project. ");
        reasoning.append("Project is in ").append(state.getCurrentPhase()).append(" phase ");
        reasoning.append("with ").append(state.getRiskLevel()).append(" risk level. ");
        
        if (readiness.getScore() >= 0.8) {
            reasoning.append("Project is ready for release. ");
        } else if (readiness.getScore() >= 0.6) {
            reasoning.append("Project is approaching release readiness. ");
        } else {
            reasoning.append("Project needs more work before release. ");
        }
        
        if (verbose) {
            reasoning.append("Detailed metrics and custom data included in response.");
        }
        
        return reasoning.toString();
    }
    
    /**
     * Format percentage for display.
     */
    private String formatPercentage(double value) {
        return String.format("%.1f%%", value * 100);
    }
    
    /**
     * Format duration for display.
     */
    private String formatDuration(long durationMs) {
        if (durationMs < 1000) {
            return durationMs + "ms";
        } else if (durationMs < 60000) {
            return String.format("%.1fs", durationMs / 1000.0);
        } else {
            return String.format("%.1fm", durationMs / 60000.0);
        }
    }
    
    /**
     * Format date/time for display.
     */
    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
