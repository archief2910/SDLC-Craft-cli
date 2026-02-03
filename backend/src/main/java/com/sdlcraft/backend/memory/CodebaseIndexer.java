package com.sdlcraft.backend.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

/**
 * Indexes codebase into vector store for RAG-based code retrieval.
 * 
 * Enables semantic search over codebase to find relevant code snippets
 * based on natural language queries.
 * 
 * Design rationale:
 * - Chunks code files into manageable pieces
 * - Stores code with metadata (file path, language, type)
 * - Enables semantic retrieval instead of random sampling
 * - Supports incremental indexing
 */
@Service
public class CodebaseIndexer {
    
    private static final Logger logger = LoggerFactory.getLogger(CodebaseIndexer.class);
    
    private static final String CODE_PREFIX = "code:";
    private static final int CHUNK_SIZE = 100; // lines per chunk
    private static final int CHUNK_OVERLAP = 10; // overlapping lines
    
    private final VectorStore vectorStore;
    
    public CodebaseIndexer(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }
    
    /**
     * Index entire codebase into vector store.
     */
    public void indexCodebase(String projectRoot) {
        logger.info("Starting codebase indexing for: {}", projectRoot);
        
        try {
            java.nio.file.Path root = java.nio.file.Paths.get(projectRoot).toAbsolutePath();
            
            // Navigate to actual project root if we're in a subdirectory
            java.nio.file.Path backendPath = root.resolve("backend/src/main/java");
            java.nio.file.Path cliPath = root.resolve("cli");
            
            // Check if paths exist, if not try parent directory (in case running from cli folder)
            if (!java.nio.file.Files.exists(backendPath)) {
                java.nio.file.Path parentRoot = root.getParent();
                if (parentRoot != null) {
                    backendPath = parentRoot.resolve("backend/src/main/java");
                    cliPath = parentRoot.resolve("cli");
                    root = parentRoot;
                }
            }
            
            int totalChunks = 0;
            
            // Index Java files
            if (java.nio.file.Files.exists(backendPath)) {
                totalChunks += indexDirectory(backendPath.toString(), ".java", "java");
            } else {
                logger.warn("Backend path not found: {}", backendPath);
            }
            
            // Index Go files
            if (java.nio.file.Files.exists(cliPath)) {
                totalChunks += indexDirectory(cliPath.toString(), ".go", "go");
            } else {
                logger.warn("CLI path not found: {}", cliPath);
            }
            
            // Index any source files in the project root
            totalChunks += indexDirectory(root.toString(), ".java", "java");
            totalChunks += indexDirectory(root.toString(), ".go", "go");
            totalChunks += indexDirectory(root.toString(), ".py", "python");
            totalChunks += indexDirectory(root.toString(), ".js", "javascript");
            totalChunks += indexDirectory(root.toString(), ".ts", "typescript");
            
            logger.info("Codebase indexing complete. Total chunks indexed: {}", totalChunks);
            
        } catch (Exception e) {
            logger.error("Failed to index codebase", e);
        }
    }
    
    /**
     * Index all files in a directory with given extension.
     * @return number of chunks indexed
     */
    private int indexDirectory(String directory, String extension, String language) {
        final int[] chunkCount = {0};
        try {
            Path startPath = Paths.get(directory);
            if (!Files.exists(startPath)) {
                return 0;
            }
            
            try (Stream<Path> paths = Files.walk(startPath)) {
                paths.filter(Files::isRegularFile)
                     .filter(p -> p.toString().endsWith(extension))
                     .filter(p -> !p.toString().contains("target")) // Skip build directories
                     .filter(p -> !p.toString().contains("node_modules"))
                     .forEach(path -> {
                         int chunks = indexFile(path, language);
                         chunkCount[0] += chunks;
                     });
            }
            
            if (chunkCount[0] > 0) {
                logger.info("Indexed {} {} files from {}", chunkCount[0], extension, directory);
            }
            
        } catch (Exception e) {
            logger.error("Failed to index directory: {}", directory, e);
        }
        return chunkCount[0];
    }
    
    /**
     * Index a single file by chunking it.
     * @return number of chunks indexed
     */
    private int indexFile(Path filePath, String language) {
        try {
            String content = Files.readString(filePath);
            String[] lines = content.split("\n");
            
            // Chunk the file
            List<CodeChunk> chunks = chunkFile(filePath.toString(), lines, language);
            
            // Store each chunk in vector store
            for (CodeChunk chunk : chunks) {
                storeCodeChunk(chunk);
            }
            
            logger.debug("Indexed file: {} ({} chunks)", filePath, chunks.size());
            return chunks.size();
            
        } catch (IOException e) {
            logger.error("Failed to index file: {}", filePath, e);
            return 0;
        }
    }
    
    /**
     * Chunk file into overlapping segments.
     * Filters out tiny chunks (< 20 lines) and whitespace-only chunks.
     */
    private List<CodeChunk> chunkFile(String filePath, String[] lines, String language) {
        List<CodeChunk> chunks = new ArrayList<>();
        
        // Minimum 20 lines of meaningful content per chunk
        final int MIN_CHUNK_LINES = 20;
        
        for (int i = 0; i < lines.length; i += CHUNK_SIZE - CHUNK_OVERLAP) {
            int endLine = Math.min(i + CHUNK_SIZE, lines.length);
            int chunkLines = endLine - i;
            
            // Skip tiny trailing chunks (they're usually just closing braces)
            if (chunkLines < MIN_CHUNK_LINES && i > 0) {
                continue;
            }
            
            StringBuilder chunkContent = new StringBuilder();
            int nonEmptyLines = 0;
            for (int j = i; j < endLine; j++) {
                chunkContent.append(lines[j]).append("\n");
                if (lines[j].trim().length() > 2) { // More than just braces/whitespace
                    nonEmptyLines++;
                }
            }
            
            // Skip chunks with mostly empty/brace-only lines
            if (nonEmptyLines < 5) {
                continue;
            }
            
            CodeChunk chunk = new CodeChunk(
                    filePath,
                    language,
                    i + 1, // start line (1-indexed)
                    endLine,
                    chunkContent.toString()
            );
            
            chunks.add(chunk);
        }
        
        return chunks;
    }
    
    /**
     * Store code chunk in vector store.
     */
    private void storeCodeChunk(CodeChunk chunk) {
        String vectorId = CODE_PREFIX + chunk.getFilePath() + ":" + chunk.getStartLine();
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", "code");
        metadata.put("filePath", chunk.getFilePath());
        metadata.put("language", chunk.getLanguage());
        metadata.put("startLine", chunk.getStartLine());
        metadata.put("endLine", chunk.getEndLine());
        
        vectorStore.storeWithAutoEmbedding(vectorId, chunk.getContent(), metadata);
    }
    
    /**
     * Retrieve relevant code chunks based on query.
     */
    public List<CodeChunk> retrieveRelevantCode(String query, int limit) {
        try {
            Map<String, Object> filter = new HashMap<>();
            filter.put("type", "code");
            
            List<VectorSearchResult> results = vectorStore.searchByText(query, limit, filter);
            
            List<CodeChunk> chunks = new ArrayList<>();
            for (VectorSearchResult result : results) {
                CodeChunk chunk = new CodeChunk(
                        (String) result.getMetadata("filePath"),
                        (String) result.getMetadata("language"),
                        ((Number) result.getMetadata("startLine")).intValue(),
                        ((Number) result.getMetadata("endLine")).intValue(),
                        result.getContent()
                );
                chunk.setSimilarityScore(result.getSimilarityScore());
                chunks.add(chunk);
            }
            
            return chunks;
            
        } catch (Exception e) {
            logger.error("Failed to retrieve relevant code", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Retrieve code chunks from specific file.
     */
    public List<CodeChunk> retrieveFromFile(String filePath, int limit) {
        try {
            Map<String, Object> filter = new HashMap<>();
            filter.put("type", "code");
            filter.put("filePath", filePath);
            
            List<VectorSearchResult> results = vectorStore.searchByText(filePath, limit, filter);
            
            List<CodeChunk> chunks = new ArrayList<>();
            for (VectorSearchResult result : results) {
                CodeChunk chunk = new CodeChunk(
                        (String) result.getMetadata("filePath"),
                        (String) result.getMetadata("language"),
                        ((Number) result.getMetadata("startLine")).intValue(),
                        ((Number) result.getMetadata("endLine")).intValue(),
                        result.getContent()
                );
                chunks.add(chunk);
            }
            
            return chunks;
            
        } catch (Exception e) {
            logger.error("Failed to retrieve code from file: {}", filePath, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Clear all indexed code from vector store.
     */
    public void clearIndex() {
        try {
            Map<String, Object> filter = new HashMap<>();
            filter.put("type", "code");
            
            int deleted = vectorStore.deleteByFilter(filter);
            logger.info("Cleared code index: {} chunks deleted", deleted);
            
        } catch (Exception e) {
            logger.error("Failed to clear code index", e);
        }
    }
    
    /**
     * Represents a chunk of code.
     */
    public static class CodeChunk {
        private final String filePath;
        private final String language;
        private final int startLine;
        private final int endLine;
        private final String content;
        private double similarityScore;
        
        public CodeChunk(String filePath, String language, int startLine, int endLine, String content) {
            this.filePath = filePath;
            this.language = language;
            this.startLine = startLine;
            this.endLine = endLine;
            this.content = content;
        }
        
        public String getFilePath() {
            return filePath;
        }
        
        public String getLanguage() {
            return language;
        }
        
        public int getStartLine() {
            return startLine;
        }
        
        public int getEndLine() {
            return endLine;
        }
        
        public String getContent() {
            return content;
        }
        
        public double getSimilarityScore() {
            return similarityScore;
        }
        
        public void setSimilarityScore(double similarityScore) {
            this.similarityScore = similarityScore;
        }
    }
}
