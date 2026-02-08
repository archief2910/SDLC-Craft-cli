package com.sdlcraft.backend.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Simple codebase indexer that reads files directly (like micro-agent).
 * 
 * No vector database needed! Just scans the filesystem and reads files.
 * This is the same approach used by micro-agent - simple and effective.
 */
@Service
@Primary
public class SimpleCodebaseIndexer {
    
    private static final Logger logger = LoggerFactory.getLogger(SimpleCodebaseIndexer.class);
    
    private static final int MAX_FILES = 200;
    private static final int MAX_FILE_SIZE = 50000; // 50KB max per file
    private static final Set<String> CODE_EXTENSIONS = Set.of(
            ".java", ".go", ".py", ".js", ".ts", ".tsx", ".jsx",
            ".rs", ".c", ".cpp", ".h", ".hpp", ".cs", ".rb", ".php"
    );
    private static final Set<String> IGNORE_DIRS = Set.of(
            "node_modules", "target", "build", "dist", ".git", 
            "__pycache__", ".idea", ".vscode", "vendor"
    );
    
    private final Map<String, String> indexedFiles = new LinkedHashMap<>();
    private String projectRoot = "";
    
    /**
     * Index a codebase by scanning and reading files directly.
     */
    public void indexCodebase(String projectPath) {
        logger.info("📁 Scanning codebase: {}", projectPath);
        indexedFiles.clear();
        
        try {
            Path root = Paths.get(projectPath).toAbsolutePath();
            this.projectRoot = root.toString();
            
            List<Path> codeFiles = new ArrayList<>();
            
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String dirName = dir.getFileName().toString();
                    if (IGNORE_DIRS.contains(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (codeFiles.size() >= MAX_FILES) {
                        return FileVisitResult.TERMINATE;
                    }
                    
                    String fileName = file.toString().toLowerCase();
                    boolean isCodeFile = CODE_EXTENSIONS.stream()
                            .anyMatch(fileName::endsWith);
                    
                    if (isCodeFile && attrs.size() < MAX_FILE_SIZE) {
                        codeFiles.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            
            // Read and index files
            for (Path file : codeFiles) {
                try {
                    String content = Files.readString(file);
                    String relativePath = root.relativize(file).toString().replace("\\", "/");
                    indexedFiles.put(relativePath, content);
                } catch (IOException e) {
                    logger.debug("Could not read file: {}", file);
                }
            }
            
            logger.info("✅ Indexed {} code files", indexedFiles.size());
            
        } catch (IOException e) {
            logger.error("Failed to scan codebase", e);
        }
    }
    
    /**
     * Generate ASCII tree of the codebase (like micro-agent).
     */
    public String generateAsciiTree() {
        if (indexedFiles.isEmpty()) {
            return "No files indexed";
        }
        
        StringBuilder tree = new StringBuilder();
        Map<String, List<String>> dirStructure = new TreeMap<>();
        
        for (String path : indexedFiles.keySet()) {
            String dir = path.contains("/") ? path.substring(0, path.lastIndexOf("/")) : ".";
            String file = path.contains("/") ? path.substring(path.lastIndexOf("/") + 1) : path;
            dirStructure.computeIfAbsent(dir, k -> new ArrayList<>()).add(file);
        }
        
        for (Map.Entry<String, List<String>> entry : dirStructure.entrySet()) {
            tree.append("📂 ").append(entry.getKey()).append("/\n");
            for (String file : entry.getValue()) {
                tree.append("   └── ").append(file).append("\n");
            }
        }
        
        return tree.toString();
    }
    
    /**
     * Get relevant code based on keywords in the query.
     * Simple keyword matching - no vectors needed!
     */
    public List<CodeChunk> getRelevantCode(String query, int limit) {
        if (indexedFiles.isEmpty()) {
            logger.warn("No files indexed! Call indexCodebase first.");
            return Collections.emptyList();
        }
        
        String[] keywords = query.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .split("\\s+");
        
        List<CodeChunk> results = new ArrayList<>();
        
        for (Map.Entry<String, String> entry : indexedFiles.entrySet()) {
            String filePath = entry.getKey();
            String content = entry.getValue();
            String lowerContent = content.toLowerCase();
            String lowerPath = filePath.toLowerCase();
            
            // Score based on keyword matches
            int score = 0;
            for (String keyword : keywords) {
                if (keyword.length() < 3) continue;
                
                // Check filename
                if (lowerPath.contains(keyword)) {
                    score += 10;
                }
                
                // Check content
                int idx = 0;
                while ((idx = lowerContent.indexOf(keyword, idx)) != -1) {
                    score += 1;
                    idx += keyword.length();
                }
            }
            
            if (score > 0) {
                String language = detectLanguage(filePath);
                
                // Truncate large files
                String displayContent = content.length() > 3000 
                        ? content.substring(0, 3000) + "\n... (truncated)"
                        : content;
                
                CodeChunk chunk = new CodeChunk(
                        filePath, language, 1, 
                        content.split("\n").length, 
                        displayContent
                );
                chunk.setSimilarityScore(score);
                results.add(chunk);
            }
        }
        
        // Sort by score and return top results
        return results.stream()
                .sorted((a, b) -> Double.compare(b.getSimilarityScore(), a.getSimilarityScore()))
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    /**
     * Get all indexed files (for full context).
     */
    public Map<String, String> getAllFiles() {
        return new LinkedHashMap<>(indexedFiles);
    }
    
    /**
     * Get a specific file's content.
     */
    public Optional<String> getFile(String path) {
        return Optional.ofNullable(indexedFiles.get(path));
    }
    
    /**
     * Check if codebase is indexed.
     */
    public boolean isIndexed() {
        return !indexedFiles.isEmpty();
    }
    
    /**
     * Get count of indexed files.
     */
    public int getFileCount() {
        return indexedFiles.size();
    }
    
    private String detectLanguage(String filePath) {
        if (filePath.endsWith(".java")) return "java";
        if (filePath.endsWith(".go")) return "go";
        if (filePath.endsWith(".py")) return "python";
        if (filePath.endsWith(".js")) return "javascript";
        if (filePath.endsWith(".ts") || filePath.endsWith(".tsx")) return "typescript";
        if (filePath.endsWith(".rs")) return "rust";
        if (filePath.endsWith(".c") || filePath.endsWith(".h")) return "c";
        if (filePath.endsWith(".cpp") || filePath.endsWith(".hpp")) return "cpp";
        return "text";
    }
    
    /**
     * Code chunk representation.
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
        
        public String getFilePath() { return filePath; }
        public String getLanguage() { return language; }
        public int getStartLine() { return startLine; }
        public int getEndLine() { return endLine; }
        public String getContent() { return content; }
        public double getSimilarityScore() { return similarityScore; }
        public void setSimilarityScore(double score) { this.similarityScore = score; }
    }
}

