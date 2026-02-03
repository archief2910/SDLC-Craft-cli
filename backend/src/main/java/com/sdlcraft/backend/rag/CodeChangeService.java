package com.sdlcraft.backend.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Service for applying code changes suggested by the RAG system.
 * 
 * Features:
 * - Apply diff patches to files
 * - Create new files
 * - Delete files
 * - Backup original files before changes
 * - Rollback capability
 */
@Service
public class CodeChangeService {
    
    private static final Logger logger = LoggerFactory.getLogger(CodeChangeService.class);
    
    private final Map<String, String> backups = new HashMap<>();
    
    /**
     * Apply a list of code changes.
     * 
     * @param changes list of changes to apply
     * @param projectRoot root directory of the project
     * @param dryRun if true, don't actually apply changes
     * @return result of applying changes
     */
    public ChangeResult applyChanges(List<CodeChange> changes, String projectRoot, boolean dryRun) {
        ChangeResult result = new ChangeResult();
        
        for (CodeChange change : changes) {
            try {
                SingleChangeResult singleResult = applyChange(change, projectRoot, dryRun);
                result.addResult(singleResult);
            } catch (Exception e) {
                logger.error("Failed to apply change to {}: {}", change.getFilePath(), e.getMessage());
                result.addResult(new SingleChangeResult(
                        change.getFilePath(),
                        change.getAction(),
                        false,
                        "Failed: " + e.getMessage()
                ));
            }
        }
        
        return result;
    }
    
    /**
     * Apply a single code change.
     */
    private SingleChangeResult applyChange(CodeChange change, String projectRoot, boolean dryRun) throws IOException {
        Path filePath = Paths.get(projectRoot, change.getFilePath());
        String action = change.getAction();
        
        switch (action.toUpperCase()) {
            case "CREATE":
                return createFile(change, filePath, dryRun);
            case "MODIFY":
                return modifyFile(change, filePath, dryRun);
            case "DELETE":
                return deleteFile(change, filePath, dryRun);
            default:
                return new SingleChangeResult(
                        change.getFilePath(),
                        action,
                        false,
                        "Unknown action: " + action
                );
        }
    }
    
    /**
     * Create a new file.
     */
    private SingleChangeResult createFile(CodeChange change, Path filePath, boolean dryRun) throws IOException {
        if (Files.exists(filePath)) {
            return new SingleChangeResult(
                    change.getFilePath(),
                    "CREATE",
                    false,
                    "File already exists"
            );
        }
        
        String content = change.getNewContent();
        if (content == null || content.isEmpty()) {
            return new SingleChangeResult(
                    change.getFilePath(),
                    "CREATE",
                    false,
                    "No content provided for new file"
            );
        }
        
        if (!dryRun) {
            // Create parent directories if needed
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, content);
            logger.info("Created file: {}", filePath);
        }
        
        return new SingleChangeResult(
                change.getFilePath(),
                "CREATE",
                true,
                dryRun ? "Would create file" : "File created"
        );
    }
    
    /**
     * Modify an existing file.
     */
    private SingleChangeResult modifyFile(CodeChange change, Path filePath, boolean dryRun) throws IOException {
        if (!Files.exists(filePath)) {
            return new SingleChangeResult(
                    change.getFilePath(),
                    "MODIFY",
                    false,
                    "File does not exist"
            );
        }
        
        String originalContent = Files.readString(filePath);
        String newContent;
        
        // Apply diff if provided
        if (change.getDiff() != null && !change.getDiff().isEmpty()) {
            newContent = applyDiff(originalContent, change.getDiff());
        } else if (change.getNewContent() != null && !change.getNewContent().isEmpty()) {
            // Replace entire content
            newContent = change.getNewContent();
        } else {
            return new SingleChangeResult(
                    change.getFilePath(),
                    "MODIFY",
                    false,
                    "No diff or new content provided"
            );
        }
        
        if (!dryRun) {
            // Backup original
            backups.put(filePath.toString(), originalContent);
            
            // Write new content
            Files.writeString(filePath, newContent);
            logger.info("Modified file: {}", filePath);
        }
        
        return new SingleChangeResult(
                change.getFilePath(),
                "MODIFY",
                true,
                dryRun ? "Would modify file" : "File modified"
        );
    }
    
    /**
     * Delete a file.
     */
    private SingleChangeResult deleteFile(CodeChange change, Path filePath, boolean dryRun) throws IOException {
        if (!Files.exists(filePath)) {
            return new SingleChangeResult(
                    change.getFilePath(),
                    "DELETE",
                    false,
                    "File does not exist"
            );
        }
        
        if (!dryRun) {
            // Backup original
            String originalContent = Files.readString(filePath);
            backups.put(filePath.toString(), originalContent);
            
            Files.delete(filePath);
            logger.info("Deleted file: {}", filePath);
        }
        
        return new SingleChangeResult(
                change.getFilePath(),
                "DELETE",
                true,
                dryRun ? "Would delete file" : "File deleted"
        );
    }
    
    /**
     * Apply a diff to content.
     * 
     * Supports unified diff format:
     * - Lines starting with '-' are removed
     * - Lines starting with '+' are added
     * - Lines starting with ' ' or no prefix are context
     */
    private String applyDiff(String originalContent, String diff) {
        String[] originalLines = originalContent.split("\n", -1);
        String[] diffLines = diff.split("\n");
        
        List<String> resultLines = new ArrayList<>(Arrays.asList(originalLines));
        
        // Parse diff and apply changes
        int offset = 0;
        
        for (int i = 0; i < diffLines.length; i++) {
            String diffLine = diffLines[i];
            
            if (diffLine.startsWith("-") && !diffLine.startsWith("---")) {
                // Line to remove
                String lineToRemove = diffLine.substring(1).trim();
                
                // Find and remove this line
                for (int j = 0; j < resultLines.size(); j++) {
                    if (resultLines.get(j).trim().equals(lineToRemove)) {
                        resultLines.remove(j);
                        break;
                    }
                }
            } else if (diffLine.startsWith("+") && !diffLine.startsWith("+++")) {
                // Line to add
                String lineToAdd = diffLine.substring(1);
                
                // Find the best position to insert
                // Look at context from previous diff line
                int insertPosition = findInsertPosition(resultLines, diffLines, i);
                if (insertPosition >= 0 && insertPosition <= resultLines.size()) {
                    resultLines.add(insertPosition, lineToAdd);
                } else {
                    // Append to end if can't find position
                    resultLines.add(lineToAdd);
                }
            }
        }
        
        return String.join("\n", resultLines);
    }
    
    /**
     * Find the best position to insert a new line based on context.
     */
    private int findInsertPosition(List<String> lines, String[] diffLines, int currentDiffIndex) {
        // Look backwards for context lines
        for (int i = currentDiffIndex - 1; i >= 0; i--) {
            String prevDiff = diffLines[i];
            if (!prevDiff.startsWith("-") && !prevDiff.startsWith("+")) {
                // This is a context line
                String contextLine = prevDiff.startsWith(" ") ? prevDiff.substring(1) : prevDiff;
                
                // Find this line in the result
                for (int j = 0; j < lines.size(); j++) {
                    if (lines.get(j).trim().equals(contextLine.trim())) {
                        return j + 1;
                    }
                }
            }
        }
        
        // Default to end
        return lines.size();
    }
    
    /**
     * Rollback all changes made in this session.
     */
    public void rollback() {
        for (Map.Entry<String, String> entry : backups.entrySet()) {
            try {
                Path path = Paths.get(entry.getKey());
                if (entry.getValue() == null) {
                    // File was created, delete it
                    Files.deleteIfExists(path);
                } else {
                    // Restore original content
                    Files.writeString(path, entry.getValue());
                }
                logger.info("Rolled back: {}", path);
            } catch (IOException e) {
                logger.error("Failed to rollback {}: {}", entry.getKey(), e.getMessage());
            }
        }
        backups.clear();
    }
    
    /**
     * Clear backup history.
     */
    public void clearBackups() {
        backups.clear();
    }
    
    /**
     * Result of applying changes.
     */
    public static class ChangeResult {
        private final List<SingleChangeResult> results = new ArrayList<>();
        private int successCount = 0;
        private int failureCount = 0;
        
        public void addResult(SingleChangeResult result) {
            results.add(result);
            if (result.isSuccess()) {
                successCount++;
            } else {
                failureCount++;
            }
        }
        
        public List<SingleChangeResult> getResults() {
            return results;
        }
        
        public int getSuccessCount() {
            return successCount;
        }
        
        public int getFailureCount() {
            return failureCount;
        }
        
        public boolean isAllSuccess() {
            return failureCount == 0 && successCount > 0;
        }
        
        public String getSummary() {
            return String.format("Applied %d changes: %d successful, %d failed",
                    results.size(), successCount, failureCount);
        }
    }
    
    /**
     * Result of a single change.
     */
    public static class SingleChangeResult {
        private final String filePath;
        private final String action;
        private final boolean success;
        private final String message;
        
        public SingleChangeResult(String filePath, String action, boolean success, String message) {
            this.filePath = filePath;
            this.action = action;
            this.success = success;
            this.message = message;
        }
        
        public String getFilePath() {
            return filePath;
        }
        
        public String getAction() {
            return action;
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getMessage() {
            return message;
        }
        
        @Override
        public String toString() {
            String status = success ? "✓" : "✗";
            return String.format("%s [%s] %s: %s", status, action, filePath, message);
        }
    }
}




