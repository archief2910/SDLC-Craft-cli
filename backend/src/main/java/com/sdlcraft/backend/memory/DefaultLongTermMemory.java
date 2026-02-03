package com.sdlcraft.backend.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Default implementation of LongTermMemory.
 * 
 * Combines PostgreSQL for structured data with vector store for semantic search.
 * Provides comprehensive command history and context management.
 * 
 * Design rationale:
 * - PostgreSQL stores structured command execution data
 * - Vector store enables semantic similarity search
 * - TTL support for data retention policies
 * - Statistics for execution pattern analysis
 */
@Service
public class DefaultLongTermMemory implements LongTermMemory {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultLongTermMemory.class);
    
    private static final String CONTEXT_PREFIX = "context:";
    private static final String COMMAND_PREFIX = "command:";
    
    private final CommandExecutionRepository executionRepository;
    private final VectorStore vectorStore;
    
    public DefaultLongTermMemory(CommandExecutionRepository executionRepository, VectorStore vectorStore) {
        this.executionRepository = executionRepository;
        this.vectorStore = vectorStore;
    }
    
    @Override
    @Transactional
    public void storeCommand(CommandExecution execution) {
        // Store in PostgreSQL
        executionRepository.save(execution);
        
        // Store in vector store for semantic search
        try {
            String vectorId = COMMAND_PREFIX + execution.getId();
            String content = buildCommandContent(execution);
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", "command");
            metadata.put("projectId", execution.getProjectId());
            metadata.put("intent", execution.getIntent());
            metadata.put("status", execution.getStatus().toString());
            metadata.put("timestamp", execution.getStartedAt().toString());
            
            vectorStore.storeWithAutoEmbedding(vectorId, content, metadata);
            
            logger.debug("Stored command in long-term memory: {}", execution.getId());
            
        } catch (Exception e) {
            // Don't fail if vector store fails
            logger.error("Failed to store command in vector store", e);
        }
    }
    
    @Override
    public List<CommandExecution> queryByIntent(String intent, int limit) {
        List<CommandExecution> results = executionRepository.findByIntentOrderByStartedAtDesc(intent);
        return results.stream().limit(limit).collect(Collectors.toList());
    }
    
    @Override
    public List<CommandExecution> queryByTimeRange(LocalDateTime startTime, LocalDateTime endTime, int limit) {
        return executionRepository.findByTimeRangeWithLimit(startTime, endTime, PageRequest.of(0, limit));
    }
    
    @Override
    public List<CommandExecution> queryByUser(String userId, int limit) {
        return executionRepository.findByUserWithLimit(userId, PageRequest.of(0, limit));
    }
    
    @Override
    public List<CommandExecution> queryByProject(String projectId, int limit) {
        return executionRepository.findByProjectWithLimit(projectId, PageRequest.of(0, limit));
    }
    
    @Override
    public List<CommandExecution> querySimilar(String query, int limit) {
        try {
            // Search vector store for similar commands
            Map<String, Object> filter = new HashMap<>();
            filter.put("type", "command");
            
            List<VectorSearchResult> vectorResults = vectorStore.searchByText(query, limit, filter);
            
            // Retrieve full command executions from PostgreSQL
            List<CommandExecution> results = new ArrayList<>();
            for (VectorSearchResult vectorResult : vectorResults) {
                String commandId = vectorResult.getId().replace(COMMAND_PREFIX, "");
                Optional<CommandExecution> execution = executionRepository.findById(commandId);
                execution.ifPresent(results::add);
            }
            
            return results;
            
        } catch (Exception e) {
            logger.error("Failed to query similar commands", e);
            return Collections.emptyList();
        }
    }
    
    @Override
    @Transactional
    public void storeContext(String projectId, Map<String, Object> context) {
        try {
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                String vectorId = CONTEXT_PREFIX + projectId + ":" + entry.getKey();
                String content = entry.getValue().toString();
                
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("type", "context");
                metadata.put("projectId", projectId);
                metadata.put("key", entry.getKey());
                metadata.put("timestamp", LocalDateTime.now().toString());
                
                vectorStore.storeWithAutoEmbedding(vectorId, content, metadata);
            }
            
            logger.debug("Stored context for project: {}", projectId);
            
        } catch (Exception e) {
            logger.error("Failed to store context", e);
        }
    }
    
    @Override
    public Map<String, Object> retrieveContext(String projectId) {
        try {
            Map<String, Object> filter = new HashMap<>();
            filter.put("type", "context");
            filter.put("projectId", projectId);
            
            // Retrieve all context entries for project
            List<VectorSearchResult> results = vectorStore.searchByText(projectId, 100, filter);
            
            Map<String, Object> context = new HashMap<>();
            for (VectorSearchResult result : results) {
                String key = (String) result.getMetadata("key");
                if (key != null) {
                    context.put(key, result.getContent());
                }
            }
            
            return context;
            
        } catch (Exception e) {
            logger.error("Failed to retrieve context", e);
            return Collections.emptyMap();
        }
    }
    
    @Override
    public List<ContextEntry> queryContextSimilar(String projectId, String query, int limit) {
        try {
            Map<String, Object> filter = new HashMap<>();
            filter.put("type", "context");
            filter.put("projectId", projectId);
            
            List<VectorSearchResult> vectorResults = vectorStore.searchByText(query, limit, filter);
            
            List<ContextEntry> entries = new ArrayList<>();
            for (VectorSearchResult result : vectorResults) {
                ContextEntry entry = new ContextEntry(
                        result.getId(),
                        projectId,
                        (String) result.getMetadata("key"),
                        result.getContent()
                );
                entry.setSimilarityScore(result.getSimilarityScore());
                entry.setMetadata(result.getMetadata());
                entries.add(entry);
            }
            
            return entries;
            
        } catch (Exception e) {
            logger.error("Failed to query similar context", e);
            return Collections.emptyList();
        }
    }
    
    @Override
    @Transactional
    public int deleteOldExecutions(LocalDateTime olderThan) {
        int deleted = executionRepository.deleteOlderThan(olderThan);
        logger.info("Deleted {} old executions", deleted);
        return deleted;
    }
    
    @Override
    public ExecutionStatistics getStatistics(String projectId) {
        long total = executionRepository.countByProjectId(projectId);
        long successful = executionRepository.countByProjectIdAndStatus(
                projectId, CommandExecution.ExecutionStatus.SUCCESS);
        long failed = executionRepository.countByProjectIdAndStatus(
                projectId, CommandExecution.ExecutionStatus.FAILURE);
        long partial = executionRepository.countByProjectIdAndStatus(
                projectId, CommandExecution.ExecutionStatus.PARTIAL);
        
        double successRate = total > 0 ? (double) successful / total : 0.0;
        
        // Calculate average duration
        List<CommandExecution> recentExecutions = executionRepository.findByProjectWithLimit(projectId, PageRequest.of(0, 100));
        long averageDuration = (long) recentExecutions.stream()
                .filter(e -> e.getDurationMs() != null)
                .mapToLong(CommandExecution::getDurationMs)
                .average()
                .orElse(0.0);
        
        // Find most common intent
        Map<String, Long> intentCounts = recentExecutions.stream()
                .collect(Collectors.groupingBy(CommandExecution::getIntent, Collectors.counting()));
        
        String mostCommonIntent = intentCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("none");
        
        return new ExecutionStatistics(
                projectId,
                total,
                successful,
                failed,
                partial,
                successRate,
                averageDuration,
                mostCommonIntent
        );
    }
    
    /**
     * Build searchable content from command execution.
     */
    private String buildCommandContent(CommandExecution execution) {
        StringBuilder content = new StringBuilder();
        
        content.append("Command: ").append(execution.getRawCommand()).append(". ");
        content.append("Intent: ").append(execution.getIntent());
        
        if (execution.getTarget() != null) {
            content.append(" targeting ").append(execution.getTarget());
        }
        
        content.append(". ");
        
        if (execution.getOutcome() != null) {
            content.append("Outcome: ").append(execution.getOutcome()).append(". ");
        }
        
        if (execution.getReasoning() != null) {
            content.append("Reasoning: ").append(execution.getReasoning());
        }
        
        return content.toString();
    }
}
