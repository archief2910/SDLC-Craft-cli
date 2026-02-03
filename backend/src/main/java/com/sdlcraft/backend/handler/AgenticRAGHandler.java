package com.sdlcraft.backend.handler;

import com.sdlcraft.backend.agent.*;
import com.sdlcraft.backend.intent.IntentResult;
import com.sdlcraft.backend.llm.LLMProvider;
import com.sdlcraft.backend.rag.*;
import com.sdlcraft.backend.sdlc.SDLCState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Agentic RAG Handler - handles commands using RAG-based code understanding.
 * 
 * This handler uses the full agent orchestration pattern (PLAN → ACT → OBSERVE → REFLECT)
 * combined with RAG for context-aware code analysis and modifications.
 * 
 * Features:
 * - Semantic code search for relevant context
 * - LLM-based code understanding and modification suggestions
 * - Automatic code change application
 * - Multi-file awareness
 */
@Component
public class AgenticRAGHandler implements IntentHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(AgenticRAGHandler.class);
    
    private final RAGService ragService;
    private final CodeChangeService codeChangeService;
    private final LLMProvider llmProvider;
    
    public AgenticRAGHandler(RAGService ragService, CodeChangeService codeChangeService, LLMProvider llmProvider) {
        this.ragService = ragService;
        this.codeChangeService = codeChangeService;
        this.llmProvider = llmProvider;
    }
    
    @Override
    public String getIntentName() {
        return "ai"; // Handles AI/RAG-based queries
    }
    
    @Override
    public boolean canHandle(IntentResult intent, SDLCState state) {
        // Can handle any intent when RAG is available
        return ragService.isAvailable();
    }
    
    @Override
    public AgentResult handle(AgentContext context) {
        logger.info("Handling agentic RAG request: {}", context.getIntent().getIntent());
        
        try {
            // Extract query from intent
            String query = extractQuery(context);
            String projectPath = extractProjectPath(context);
            boolean applyChanges = shouldApplyChanges(context);
            
            // Phase 1: PLAN - Query RAG for understanding and suggestions
            logger.debug("PLAN phase: Querying RAG for suggestions");
            RAGResponse ragResponse = ragService.query(query, projectPath);
            
            if (!ragResponse.isSuccess()) {
                return AgentResult.builder()
                        .agentType("agentic-rag")
                        .phase(AgentPhase.PLAN)
                        .status(AgentStatus.FAILURE)
                        .error(ragResponse.getError())
                        .reasoning("RAG query failed")
                        .build();
            }
            
            // Phase 2: ACT - Apply changes if requested
            CodeChangeService.ChangeResult changeResult = null;
            if (applyChanges && !ragResponse.getChanges().isEmpty()) {
                logger.debug("ACT phase: Applying {} changes", ragResponse.getChanges().size());
                
                // First do a dry run to validate
                CodeChangeService.ChangeResult dryRun = codeChangeService.applyChanges(
                        ragResponse.getChanges(), projectPath, true);
                
                if (dryRun.getFailureCount() == 0) {
                    // Apply for real
                    changeResult = codeChangeService.applyChanges(
                            ragResponse.getChanges(), projectPath, false);
                } else {
                    logger.warn("Dry run found issues, not applying changes");
                }
            }
            
            // Phase 3: OBSERVE - Validate results
            // (In a real implementation, we could run tests or linters here)
            
            // Phase 4: REFLECT - Build response with insights
            Map<String, Object> resultData = buildResultData(ragResponse, changeResult);
            
            AgentStatus status = determineStatus(ragResponse, changeResult);
            String reasoning = buildReasoning(ragResponse, changeResult);
            
            return AgentResult.builder()
                    .agentType("agentic-rag")
                    .phase(AgentPhase.REFLECT)
                    .status(status)
                    .data(resultData)
                    .reasoning(reasoning)
                    .build();
            
        } catch (Exception e) {
            logger.error("Agentic RAG handler failed", e);
            return AgentResult.builder()
                    .agentType("agentic-rag")
                    .phase(AgentPhase.ACT)
                    .status(AgentStatus.FAILURE)
                    .error("Failed: " + e.getMessage())
                    .reasoning("Exception during RAG processing")
                    .build();
        }
    }
    
    /**
     * Extract the query from the context.
     */
    private String extractQuery(AgentContext context) {
        IntentResult intent = context.getIntent();
        
        // Try to get query from parameters
        Object queryParam = context.getParameter("query");
        if (queryParam != null) {
            return queryParam.toString();
        }
        
        // Fall back to raw command if available
        Object rawCommand = context.getParameter("rawCommand");
        if (rawCommand != null) {
            return rawCommand.toString();
        }
        
        // Build query from intent
        StringBuilder query = new StringBuilder();
        query.append(intent.getIntent());
        if (intent.getTarget() != null && !intent.getTarget().isEmpty()) {
            query.append(" ").append(intent.getTarget());
        }
        if (intent.getModifiers() != null) {
            for (Map.Entry<String, String> entry : intent.getModifiers().entrySet()) {
                query.append(" ").append(entry.getKey()).append(":").append(entry.getValue());
            }
        }
        
        return query.toString();
    }
    
    /**
     * Extract project path from context.
     */
    private String extractProjectPath(AgentContext context) {
        Object projectPath = context.getParameter("projectPath");
        if (projectPath != null) {
            return projectPath.toString();
        }
        return ".";
    }
    
    /**
     * Check if changes should be applied automatically.
     */
    private boolean shouldApplyChanges(AgentContext context) {
        Object apply = context.getParameter("applyChanges");
        if (apply != null) {
            return Boolean.parseBoolean(apply.toString());
        }
        return false; // Default to not applying automatically
    }
    
    /**
     * Build result data from RAG response and change results.
     */
    private Map<String, Object> buildResultData(RAGResponse ragResponse, CodeChangeService.ChangeResult changeResult) {
        Map<String, Object> data = new HashMap<>();
        
        data.put("explanation", ragResponse.getExplanation());
        data.put("contextChunks", ragResponse.getContextChunks());
        data.put("referencedFiles", ragResponse.getReferencedFiles());
        
        // Add suggested changes
        List<Map<String, Object>> changesList = ragResponse.getChanges().stream()
                .map(change -> {
                    Map<String, Object> changeMap = new HashMap<>();
                    changeMap.put("file", change.getFilePath());
                    changeMap.put("action", change.getAction());
                    changeMap.put("description", change.getDescription());
                    if (change.getDiff() != null) {
                        changeMap.put("diff", change.getDiff());
                    }
                    return changeMap;
                })
                .collect(Collectors.toList());
        data.put("suggestedChanges", changesList);
        
        // Add commands
        if (ragResponse.getCommands() != null && !ragResponse.getCommands().isEmpty()) {
            data.put("commands", ragResponse.getCommands());
        }
        
        // Add change application results
        if (changeResult != null) {
            data.put("changesApplied", true);
            data.put("changesSummary", changeResult.getSummary());
            data.put("successCount", changeResult.getSuccessCount());
            data.put("failureCount", changeResult.getFailureCount());
            
            List<String> results = changeResult.getResults().stream()
                    .map(CodeChangeService.SingleChangeResult::toString)
                    .collect(Collectors.toList());
            data.put("changeResults", results);
        } else {
            data.put("changesApplied", false);
        }
        
        return data;
    }
    
    /**
     * Determine overall status.
     */
    private AgentStatus determineStatus(RAGResponse ragResponse, CodeChangeService.ChangeResult changeResult) {
        if (!ragResponse.isSuccess()) {
            return AgentStatus.FAILURE;
        }
        
        if (changeResult != null && changeResult.getFailureCount() > 0) {
            return AgentStatus.PARTIAL;
        }
        
        return AgentStatus.SUCCESS;
    }
    
    /**
     * Build reasoning string.
     */
    private String buildReasoning(RAGResponse ragResponse, CodeChangeService.ChangeResult changeResult) {
        StringBuilder reasoning = new StringBuilder();
        
        reasoning.append("Analyzed codebase with ").append(ragResponse.getContextChunks())
                .append(" relevant code chunks. ");
        
        if (ragResponse.getChanges().isEmpty()) {
            reasoning.append("No code changes suggested. ");
        } else {
            reasoning.append("Suggested ").append(ragResponse.getChanges().size()).append(" changes. ");
        }
        
        if (changeResult != null) {
            reasoning.append(changeResult.getSummary());
        }
        
        return reasoning.toString();
    }
    
    @Override
    public String getHelpText() {
        return "Agentic RAG handler that uses semantic code search and LLM to understand and modify code based on natural language requests.";
    }
}




