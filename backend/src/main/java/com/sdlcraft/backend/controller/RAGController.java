package com.sdlcraft.backend.controller;

import com.sdlcraft.backend.memory.CodebaseIndexer;
import com.sdlcraft.backend.rag.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST Controller for RAG (Retrieval-Augmented Generation) operations.
 * 
 * Provides endpoints for:
 * - Querying the codebase with natural language
 * - Applying suggested code changes
 * - Re-indexing the codebase
 */
@RestController
@RequestMapping("/api/rag")
public class RAGController {
    
    private static final Logger logger = LoggerFactory.getLogger(RAGController.class);
    
    private final RAGService ragService;
    private final CodeChangeService codeChangeService;
    private final CodebaseIndexer codebaseIndexer;
    
    public RAGController(RAGService ragService, CodeChangeService codeChangeService, CodebaseIndexer codebaseIndexer) {
        this.ragService = ragService;
        this.codeChangeService = codeChangeService;
        this.codebaseIndexer = codebaseIndexer;
    }
    
    /**
     * Query the codebase with natural language.
     */
    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> query(@RequestBody RAGQueryRequest request) {
        logger.info("Received RAG query: {}", request.getQuery());
        
        try {
            RAGResponse response;
            
            if (request.getFocusFile() != null && !request.getFocusFile().isEmpty()) {
                response = ragService.queryWithFocus(
                        request.getQuery(),
                        request.getFocusFile(),
                        request.getProjectPath()
                );
            } else {
                response = ragService.query(request.getQuery(), request.getProjectPath());
            }
            
            return ResponseEntity.ok(buildQueryResponse(response));
            
        } catch (Exception e) {
            logger.error("RAG query failed", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Apply suggested code changes.
     */
    @PostMapping("/apply")
    public ResponseEntity<Map<String, Object>> applyChanges(@RequestBody ApplyChangesRequest request) {
        logger.info("Applying {} changes", request.getChanges().size());
        
        try {
            CodeChangeService.ChangeResult result = codeChangeService.applyChanges(
                    request.getChanges(),
                    request.getProjectPath(),
                    request.isDryRun()
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isAllSuccess());
            response.put("summary", result.getSummary());
            response.put("successCount", result.getSuccessCount());
            response.put("failureCount", result.getFailureCount());
            response.put("results", result.getResults().stream()
                    .map(r -> Map.of(
                            "file", r.getFilePath(),
                            "action", r.getAction(),
                            "success", r.isSuccess(),
                            "message", r.getMessage()
                    ))
                    .collect(Collectors.toList()));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Apply changes failed", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Rollback the last set of changes.
     */
    @PostMapping("/rollback")
    public ResponseEntity<Map<String, Object>> rollback() {
        logger.info("Rolling back changes");
        
        try {
            codeChangeService.rollback();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Changes rolled back successfully"
            ));
        } catch (Exception e) {
            logger.error("Rollback failed", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Re-index the codebase.
     */
    @PostMapping("/index")
    public ResponseEntity<Map<String, Object>> indexCodebase(@RequestBody IndexRequest request) {
        logger.info("Indexing codebase: {}", request.getProjectPath());
        
        try {
            ragService.reindexCodebase(request.getProjectPath());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Codebase indexed successfully"
            ));
        } catch (Exception e) {
            logger.error("Indexing failed", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Get RAG service status.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "available", ragService.isAvailable(),
                "vectorStoreAvailable", ragService.isAvailable()
        ));
    }
    
    /**
     * Debug endpoint to see actual code chunks retrieved from Pinecone.
     */
    @PostMapping("/debug/search")
    public ResponseEntity<Map<String, Object>> debugSearch(@RequestBody Map<String, Object> request) {
        String query = (String) request.get("query");
        int limit = request.containsKey("limit") ? ((Number) request.get("limit")).intValue() : 5;
        
        logger.info("Debug search for: {}", query);
        
        try {
            List<CodebaseIndexer.CodeChunk> chunks = codebaseIndexer.retrieveRelevantCode(query, limit);
            
            List<Map<String, Object>> results = chunks.stream()
                    .map(chunk -> {
                        Map<String, Object> chunkMap = new HashMap<>();
                        chunkMap.put("filePath", chunk.getFilePath());
                        chunkMap.put("language", chunk.getLanguage());
                        chunkMap.put("startLine", chunk.getStartLine());
                        chunkMap.put("endLine", chunk.getEndLine());
                        chunkMap.put("score", chunk.getSimilarityScore());
                        chunkMap.put("contentLength", chunk.getContent() != null ? chunk.getContent().length() : 0);
                        chunkMap.put("contentPreview", chunk.getContent() != null 
                                ? chunk.getContent().substring(0, Math.min(500, chunk.getContent().length())) + "..."
                                : "NULL");
                        return chunkMap;
                    })
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "query", query,
                    "chunksFound", chunks.size(),
                    "results", results
            ));
            
        } catch (Exception e) {
            logger.error("Debug search failed", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Build response map from RAGResponse.
     */
    private Map<String, Object> buildQueryResponse(RAGResponse response) {
        Map<String, Object> result = new HashMap<>();
        
        result.put("success", response.isSuccess());
        
        if (!response.isSuccess()) {
            result.put("error", response.getError());
            return result;
        }
        
        result.put("query", response.getQuery());
        result.put("explanation", response.getExplanation());
        result.put("contextChunks", response.getContextChunks());
        result.put("referencedFiles", response.getReferencedFiles());
        
        // Format changes
        List<Map<String, Object>> changes = response.getChanges().stream()
                .map(change -> {
                    Map<String, Object> changeMap = new HashMap<>();
                    changeMap.put("file", change.getFilePath());
                    changeMap.put("action", change.getAction());
                    changeMap.put("description", change.getDescription());
                    if (change.getDiff() != null) {
                        changeMap.put("diff", change.getDiff());
                    }
                    if (change.getNewContent() != null) {
                        changeMap.put("newContent", change.getNewContent());
                    }
                    return changeMap;
                })
                .collect(Collectors.toList());
        result.put("changes", changes);
        
        if (response.getCommands() != null && !response.getCommands().isEmpty()) {
            result.put("commands", response.getCommands());
        }
        
        return result;
    }
    
    // Request DTOs
    
    public static class RAGQueryRequest {
        private String query;
        private String projectPath = ".";
        private String focusFile;
        
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        
        public String getProjectPath() { return projectPath; }
        public void setProjectPath(String projectPath) { this.projectPath = projectPath; }
        
        public String getFocusFile() { return focusFile; }
        public void setFocusFile(String focusFile) { this.focusFile = focusFile; }
    }
    
    public static class ApplyChangesRequest {
        private List<CodeChange> changes;
        private String projectPath = ".";
        private boolean dryRun = false;
        
        public List<CodeChange> getChanges() { return changes; }
        public void setChanges(List<CodeChange> changes) { this.changes = changes; }
        
        public String getProjectPath() { return projectPath; }
        public void setProjectPath(String projectPath) { this.projectPath = projectPath; }
        
        public boolean isDryRun() { return dryRun; }
        public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }
    }
    
    public static class IndexRequest {
        private String projectPath = ".";
        
        public String getProjectPath() { return projectPath; }
        public void setProjectPath(String projectPath) { this.projectPath = projectPath; }
    }
}

