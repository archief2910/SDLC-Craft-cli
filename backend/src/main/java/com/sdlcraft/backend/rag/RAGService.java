package com.sdlcraft.backend.rag;

import com.sdlcraft.backend.llm.LLMException;
import com.sdlcraft.backend.llm.LLMProvider;
import com.sdlcraft.backend.memory.CodebaseIndexer;
import com.sdlcraft.backend.memory.CodebaseIndexer.CodeChunk;
import com.sdlcraft.backend.memory.VectorSearchResult;
import com.sdlcraft.backend.memory.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG (Retrieval-Augmented Generation) Service.
 * 
 * Combines vector store retrieval with LLM generation for context-aware
 * code understanding and modification suggestions.
 * 
 * Features:
 * - Semantic code search using vector embeddings
 * - Context-aware LLM prompting
 * - Code change suggestions with file paths
 * - Multi-file awareness for complex changes
 */
@Service
public class RAGService {
    
    private static final Logger logger = LoggerFactory.getLogger(RAGService.class);
    private static final int DEFAULT_CONTEXT_CHUNKS = 5;
    private static final int MAX_CONTEXT_LENGTH = 8000;
    
    private final VectorStore vectorStore;
    private final LLMProvider llmProvider;
    private final CodebaseIndexer codebaseIndexer;
    
    public RAGService(VectorStore vectorStore, LLMProvider llmProvider, CodebaseIndexer codebaseIndexer) {
        this.vectorStore = vectorStore;
        this.llmProvider = llmProvider;
        this.codebaseIndexer = codebaseIndexer;
    }
    
    /**
     * Process a natural language query with RAG.
     * 
     * @param query the user's natural language query
     * @param projectPath the project path for context
     * @return RAG response with suggestions
     */
    public RAGResponse query(String query, String projectPath) {
        logger.info("Processing RAG query: {}", query);
        
        try {
            // Step 1: Retrieve relevant code context
            List<CodeChunk> relevantCode = retrieveContext(query);
            
            // Step 2: Build context-aware prompt
            String prompt = buildPrompt(query, relevantCode, projectPath);
            
            // Step 3: Get LLM response
            String llmResponse = llmProvider.complete(prompt, Map.of(
                    "temperature", 0.3,
                    "max_tokens", 2000
            ));
            
            // Step 4: Parse LLM response into structured changes
            RAGResponse response = parseResponse(llmResponse, relevantCode);
            response.setQuery(query);
            response.setContextChunks(relevantCode.size());
            
            logger.info("RAG query completed with {} suggested changes", response.getChanges().size());
            return response;
            
        } catch (LLMException e) {
            logger.error("LLM failed during RAG query", e);
            return RAGResponse.error("LLM service unavailable: " + e.getMessage());
        } catch (Exception e) {
            logger.error("RAG query failed", e);
            return RAGResponse.error("Failed to process query: " + e.getMessage());
        }
    }
    
    /**
     * Query with specific file focus.
     */
    public RAGResponse queryWithFocus(String query, String targetFile, String projectPath) {
        logger.info("Processing focused RAG query on file: {}", targetFile);
        
        try {
            // Retrieve code from specific file
            List<CodeChunk> fileChunks = codebaseIndexer.retrieveFromFile(targetFile, 10);
            
            // Also get related context
            List<CodeChunk> relatedChunks = retrieveContext(query);
            
            // Combine and deduplicate
            Set<String> seenFiles = new HashSet<>();
            List<CodeChunk> combinedChunks = new ArrayList<>();
            
            for (CodeChunk chunk : fileChunks) {
                String key = chunk.getFilePath() + ":" + chunk.getStartLine();
                if (seenFiles.add(key)) {
                    combinedChunks.add(chunk);
                }
            }
            
            for (CodeChunk chunk : relatedChunks) {
                String key = chunk.getFilePath() + ":" + chunk.getStartLine();
                if (seenFiles.add(key) && combinedChunks.size() < 10) {
                    combinedChunks.add(chunk);
                }
            }
            
            // Build focused prompt
            String prompt = buildFocusedPrompt(query, targetFile, combinedChunks, projectPath);
            
            // Get LLM response
            String llmResponse = llmProvider.complete(prompt, Map.of(
                    "temperature", 0.3,
                    "max_tokens", 2000
            ));
            
            // Parse response
            RAGResponse response = parseResponse(llmResponse, combinedChunks);
            response.setQuery(query);
            response.setFocusFile(targetFile);
            response.setContextChunks(combinedChunks.size());
            
            return response;
            
        } catch (Exception e) {
            logger.error("Focused RAG query failed", e);
            return RAGResponse.error("Failed to process query: " + e.getMessage());
        }
    }
    
    /**
     * Retrieve relevant code chunks for a query.
     */
    private List<CodeChunk> retrieveContext(String query) {
        return codebaseIndexer.retrieveRelevantCode(query, DEFAULT_CONTEXT_CHUNKS);
    }
    
    /**
     * Build the prompt for LLM with retrieved context.
     */
    private String buildPrompt(String query, List<CodeChunk> context, String projectPath) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are an expert software engineer assistant. ");
        prompt.append("Your task is to analyze code and suggest precise changes based on the user's request.\n\n");
        
        prompt.append("## Project: ").append(projectPath).append("\n\n");
        
        prompt.append("## Relevant Code Context:\n\n");
        
        int totalLength = 0;
        for (CodeChunk chunk : context) {
            String chunkText = formatChunk(chunk);
            if (totalLength + chunkText.length() > MAX_CONTEXT_LENGTH) {
                break;
            }
            prompt.append(chunkText).append("\n");
            totalLength += chunkText.length();
        }
        
        prompt.append("\n## User Request:\n");
        prompt.append(query).append("\n\n");
        
        prompt.append("## Instructions:\n");
        prompt.append("1. Analyze the code context and understand the existing patterns\n");
        prompt.append("2. Provide specific, actionable changes to address the user's request\n");
        prompt.append("3. Format your response as follows:\n\n");
        
        prompt.append("### EXPLANATION:\n");
        prompt.append("[Brief explanation of what changes are needed and why]\n\n");
        
        prompt.append("### CHANGES:\n");
        prompt.append("For each file that needs to be modified, provide:\n\n");
        prompt.append("#### FILE: [full file path]\n");
        prompt.append("#### ACTION: [CREATE|MODIFY|DELETE]\n");
        prompt.append("#### DESCRIPTION: [what this change does]\n\n");
        prompt.append("If MODIFY, provide:\n");
        prompt.append("```diff\n");
        prompt.append("- [line to remove]\n");
        prompt.append("+ [line to add]\n");
        prompt.append("```\n\n");
        prompt.append("If CREATE, provide the full file content.\n\n");
        
        prompt.append("### COMMANDS:\n");
        prompt.append("[Any shell commands that should be run after the changes, e.g., build commands]\n");
        
        return prompt.toString();
    }
    
    /**
     * Build focused prompt for specific file modifications.
     */
    private String buildFocusedPrompt(String query, String targetFile, List<CodeChunk> context, String projectPath) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are an expert software engineer assistant. ");
        prompt.append("Focus on modifying the specific file requested.\n\n");
        
        prompt.append("## Target File: ").append(targetFile).append("\n");
        prompt.append("## Project: ").append(projectPath).append("\n\n");
        
        prompt.append("## Current File Content and Related Code:\n\n");
        
        for (CodeChunk chunk : context) {
            prompt.append(formatChunk(chunk)).append("\n");
        }
        
        prompt.append("\n## User Request:\n");
        prompt.append(query).append("\n\n");
        
        prompt.append("## Instructions:\n");
        prompt.append("Provide specific changes for ").append(targetFile).append("\n\n");
        
        prompt.append("### EXPLANATION:\n");
        prompt.append("[Why these changes are needed]\n\n");
        
        prompt.append("### CHANGES:\n");
        prompt.append("#### FILE: ").append(targetFile).append("\n");
        prompt.append("#### ACTION: MODIFY\n");
        prompt.append("#### DESCRIPTION: [what this change does]\n\n");
        prompt.append("```diff\n");
        prompt.append("[provide the diff]\n");
        prompt.append("```\n");
        
        return prompt.toString();
    }
    
    /**
     * Format a code chunk for the prompt.
     */
    private String formatChunk(CodeChunk chunk) {
        StringBuilder sb = new StringBuilder();
        sb.append("### File: ").append(chunk.getFilePath());
        sb.append(" (lines ").append(chunk.getStartLine()).append("-").append(chunk.getEndLine()).append(")\n");
        sb.append("```").append(chunk.getLanguage()).append("\n");
        sb.append(chunk.getContent());
        sb.append("```\n");
        return sb.toString();
    }
    
    /**
     * Parse LLM response into structured changes.
     */
    private RAGResponse parseResponse(String llmResponse, List<CodeChunk> context) {
        RAGResponse response = new RAGResponse();
        response.setRawResponse(llmResponse);
        
        // Extract explanation
        String explanation = extractSection(llmResponse, "EXPLANATION:", "CHANGES:");
        response.setExplanation(explanation != null ? explanation.trim() : "");
        
        // Extract changes
        List<CodeChange> changes = extractChanges(llmResponse);
        response.setChanges(changes);
        
        // Extract commands
        String commands = extractSection(llmResponse, "COMMANDS:", null);
        if (commands != null && !commands.trim().isEmpty()) {
            response.setCommands(Arrays.asList(commands.trim().split("\n")));
        }
        
        // Add referenced files
        Set<String> referencedFiles = context.stream()
                .map(CodeChunk::getFilePath)
                .collect(Collectors.toSet());
        response.setReferencedFiles(new ArrayList<>(referencedFiles));
        
        return response;
    }
    
    /**
     * Extract a section from the response.
     */
    private String extractSection(String response, String startMarker, String endMarker) {
        int start = response.indexOf(startMarker);
        if (start == -1) {
            // Try without ### prefix
            startMarker = startMarker.replace("### ", "");
            start = response.indexOf(startMarker);
        }
        if (start == -1) return null;
        
        start += startMarker.length();
        
        int end = response.length();
        if (endMarker != null) {
            int endIdx = response.indexOf(endMarker, start);
            if (endIdx == -1) {
                // Try with ### prefix
                endIdx = response.indexOf("### " + endMarker, start);
            }
            if (endIdx > start) {
                end = endIdx;
            }
        }
        
        return response.substring(start, end);
    }
    
    /**
     * Extract code changes from the response.
     */
    private List<CodeChange> extractChanges(String response) {
        List<CodeChange> changes = new ArrayList<>();
        
        String changesSection = extractSection(response, "CHANGES:", "COMMANDS:");
        if (changesSection == null) {
            changesSection = response;
        }
        
        // Parse file blocks
        String[] blocks = changesSection.split("(?=#### FILE:|(?=FILE:))");
        
        for (String block : blocks) {
            if (block.trim().isEmpty()) continue;
            
            CodeChange change = parseChangeBlock(block);
            if (change != null && change.getFilePath() != null) {
                changes.add(change);
            }
        }
        
        return changes;
    }
    
    /**
     * Parse a single change block.
     */
    private CodeChange parseChangeBlock(String block) {
        CodeChange change = new CodeChange();
        
        // Extract file path
        String filePath = extractValue(block, "FILE:");
        if (filePath == null) return null;
        change.setFilePath(filePath.trim());
        
        // Extract action
        String action = extractValue(block, "ACTION:");
        change.setAction(action != null ? action.trim().toUpperCase() : "MODIFY");
        
        // Extract description
        String description = extractValue(block, "DESCRIPTION:");
        change.setDescription(description != null ? description.trim() : "");
        
        // Extract diff or content
        int diffStart = block.indexOf("```diff");
        if (diffStart != -1) {
            int diffEnd = block.indexOf("```", diffStart + 7);
            if (diffEnd > diffStart) {
                String diff = block.substring(diffStart + 7, diffEnd).trim();
                change.setDiff(diff);
            }
        } else {
            // Look for any code block
            int codeStart = block.indexOf("```");
            if (codeStart != -1) {
                int codeEnd = block.indexOf("```", codeStart + 3);
                if (codeEnd > codeStart) {
                    String code = block.substring(codeStart + 3, codeEnd).trim();
                    // Remove language identifier if present
                    if (code.contains("\n")) {
                        code = code.substring(code.indexOf("\n") + 1);
                    }
                    change.setNewContent(code);
                }
            }
        }
        
        return change;
    }
    
    /**
     * Extract a value from a block.
     */
    private String extractValue(String block, String key) {
        int start = block.indexOf(key);
        if (start == -1) return null;
        
        start += key.length();
        int end = block.indexOf("\n", start);
        if (end == -1) end = block.length();
        
        return block.substring(start, end).trim();
    }
    
    /**
     * Re-index the codebase.
     */
    public void reindexCodebase(String projectPath) {
        logger.info("Re-indexing codebase: {}", projectPath);
        codebaseIndexer.clearIndex();
        codebaseIndexer.indexCodebase(projectPath);
    }
    
    /**
     * Check if RAG service is available.
     */
    public boolean isAvailable() {
        return vectorStore.isAvailable() && llmProvider.isAvailable();
    }
}





