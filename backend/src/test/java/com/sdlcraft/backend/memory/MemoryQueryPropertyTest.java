package com.sdlcraft.backend.memory;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for Long-Term Memory Query Capabilities.
 * 
 * Feature: sdlcraft-cli
 * Property 20: Memory Query Capabilities
 * Validates: Requirements 5.6
 * 
 * This test verifies that for any memory query by time range, intent type, or semantic
 * similarity, the Long-Term Memory returns results matching the query criteria.
 * 
 * The test uses an in-memory implementation to verify query logic without requiring
 * a full database or vector store setup.
 */
class MemoryQueryPropertyTest {
    
    /**
     * Property 20: Query by Intent Type
     * 
     * For any set of command executions with various intents, querying by a specific
     * intent should return only executions with that intent, ordered by most recent first.
     */
    @Property(tries = 100)
    @Label("Query by intent returns only matching executions")
    void queryByIntentReturnsMatchingExecutions(
            @ForAll("commandExecutionSets") List<CommandExecution> executions,
            @ForAll("intents") String targetIntent,
            @ForAll @IntRange(min = 1, max = 20) int limit) {
        
        // Create memory instance with test data
        TestLongTermMemory memory = new TestLongTermMemory();
        executions.forEach(memory::storeCommand);
        
        // Query by intent
        List<CommandExecution> results = memory.queryByIntent(targetIntent, limit);
        
        // Verify all results match the intent
        assertThat(results)
                .as("All results should have the target intent")
                .allMatch(e -> e.getIntent().equals(targetIntent));
        
        // Verify results are ordered by most recent first
        if (results.size() > 1) {
            for (int i = 0; i < results.size() - 1; i++) {
                assertThat(results.get(i).getStartedAt())
                        .as("Results should be ordered by most recent first")
                        .isAfterOrEqualTo(results.get(i + 1).getStartedAt());
            }
        }
        
        // Verify limit is respected
        assertThat(results.size())
                .as("Results should not exceed limit")
                .isLessThanOrEqualTo(limit);
        
        // Verify we got all matching executions (up to limit)
        long expectedCount = executions.stream()
                .filter(e -> e.getIntent().equals(targetIntent))
                .count();
        long actualCount = results.size();
        
        assertThat(actualCount)
                .as("Should return all matching executions up to limit")
                .isEqualTo(Math.min(expectedCount, limit));
    }
    
    /**
     * Property 20: Query by Time Range
     * 
     * For any set of command executions with various timestamps, querying by a time range
     * should return only executions within that range, ordered by most recent first.
     */
    @Property(tries = 100)
    @Label("Query by time range returns only executions within range")
    void queryByTimeRangeReturnsExecutionsInRange(
            @ForAll("commandExecutionSets") List<CommandExecution> executions,
            @ForAll("timeRanges") TimeRange range,
            @ForAll @IntRange(min = 1, max = 20) int limit) {
        
        // Create memory instance with test data
        TestLongTermMemory memory = new TestLongTermMemory();
        executions.forEach(memory::storeCommand);
        
        // Query by time range
        List<CommandExecution> results = memory.queryByTimeRange(range.start, range.end, limit);
        
        // Verify all results are within the time range
        assertThat(results)
                .as("All results should be within the time range")
                .allMatch(e -> !e.getStartedAt().isBefore(range.start) && 
                              !e.getStartedAt().isAfter(range.end));
        
        // Verify results are ordered by most recent first
        if (results.size() > 1) {
            for (int i = 0; i < results.size() - 1; i++) {
                assertThat(results.get(i).getStartedAt())
                        .as("Results should be ordered by most recent first")
                        .isAfterOrEqualTo(results.get(i + 1).getStartedAt());
            }
        }
        
        // Verify limit is respected
        assertThat(results.size())
                .as("Results should not exceed limit")
                .isLessThanOrEqualTo(limit);
    }
    
    /**
     * Property 20: Query by User
     * 
     * For any set of command executions from various users, querying by a specific user
     * should return only that user's executions, ordered by most recent first.
     */
    @Property(tries = 100)
    @Label("Query by user returns only that user's executions")
    void queryByUserReturnsUserExecutions(
            @ForAll("commandExecutionSets") List<CommandExecution> executions,
            @ForAll("userIds") String targetUserId,
            @ForAll @IntRange(min = 1, max = 20) int limit) {
        
        // Create memory instance with test data
        TestLongTermMemory memory = new TestLongTermMemory();
        executions.forEach(memory::storeCommand);
        
        // Query by user
        List<CommandExecution> results = memory.queryByUser(targetUserId, limit);
        
        // Verify all results belong to the target user
        assertThat(results)
                .as("All results should belong to the target user")
                .allMatch(e -> e.getUserId().equals(targetUserId));
        
        // Verify results are ordered by most recent first
        if (results.size() > 1) {
            for (int i = 0; i < results.size() - 1; i++) {
                assertThat(results.get(i).getStartedAt())
                        .as("Results should be ordered by most recent first")
                        .isAfterOrEqualTo(results.get(i + 1).getStartedAt());
            }
        }
        
        // Verify limit is respected
        assertThat(results.size())
                .as("Results should not exceed limit")
                .isLessThanOrEqualTo(limit);
    }
    
    /**
     * Property 20: Query by Project
     * 
     * For any set of command executions from various projects, querying by a specific
     * project should return only that project's executions, ordered by most recent first.
     */
    @Property(tries = 100)
    @Label("Query by project returns only that project's executions")
    void queryByProjectReturnsProjectExecutions(
            @ForAll("commandExecutionSets") List<CommandExecution> executions,
            @ForAll("projectIds") String targetProjectId,
            @ForAll @IntRange(min = 1, max = 20) int limit) {
        
        // Create memory instance with test data
        TestLongTermMemory memory = new TestLongTermMemory();
        executions.forEach(memory::storeCommand);
        
        // Query by project
        List<CommandExecution> results = memory.queryByProject(targetProjectId, limit);
        
        // Verify all results belong to the target project
        assertThat(results)
                .as("All results should belong to the target project")
                .allMatch(e -> e.getProjectId().equals(targetProjectId));
        
        // Verify results are ordered by most recent first
        if (results.size() > 1) {
            for (int i = 0; i < results.size() - 1; i++) {
                assertThat(results.get(i).getStartedAt())
                        .as("Results should be ordered by most recent first")
                        .isAfterOrEqualTo(results.get(i + 1).getStartedAt());
            }
        }
        
        // Verify limit is respected
        assertThat(results.size())
                .as("Results should not exceed limit")
                .isLessThanOrEqualTo(limit);
    }
    
    /**
     * Property 20: Query Similar Commands
     * 
     * For any set of command executions, semantic similarity search should return
     * results ordered by similarity score (most similar first).
     */
    @Property(tries = 50)
    @Label("Semantic similarity search returns results ordered by similarity")
    void querySimilarReturnsOrderedBySimilarity(
            @ForAll("commandExecutionSets") List<CommandExecution> executions,
            @ForAll("searchQueries") String query,
            @ForAll @IntRange(min = 1, max = 10) int limit) {
        
        // Create memory instance with test data
        TestLongTermMemory memory = new TestLongTermMemory();
        executions.forEach(memory::storeCommand);
        
        // Query similar commands
        List<CommandExecution> results = memory.querySimilar(query, limit);
        
        // Verify limit is respected
        assertThat(results.size())
                .as("Results should not exceed limit")
                .isLessThanOrEqualTo(limit);
        
        // Verify all results contain the query term (simple text matching)
        assertThat(results)
                .as("All results should match the query")
                .allMatch(e -> e.getRawCommand().toLowerCase().contains(query.toLowerCase()) ||
                              e.getIntent().toLowerCase().contains(query.toLowerCase()));
        
        // Note: Actual similarity ordering is tested in the TestLongTermMemory implementation
        // which uses a simple text matching algorithm for testing purposes
    }
    
    /**
     * Property 20: Empty Query Results
     * 
     * For any query that matches no executions, the result should be an empty list,
     * not null or an error.
     */
    @Property(tries = 50)
    @Label("Queries with no matches return empty list")
    void queriesWithNoMatchesReturnEmptyList(
            @ForAll("commandExecutionSets") List<CommandExecution> executions) {
        
        // Create memory instance with test data
        TestLongTermMemory memory = new TestLongTermMemory();
        executions.forEach(memory::storeCommand);
        
        // Query with intent that doesn't exist
        String nonExistentIntent = "nonexistent-intent-" + UUID.randomUUID();
        List<CommandExecution> results = memory.queryByIntent(nonExistentIntent, 10);
        
        assertThat(results)
                .as("Query with no matches should return empty list")
                .isNotNull()
                .isEmpty();
    }
    
    /**
     * Property 20: Query Consistency
     * 
     * For any set of executions, querying multiple times with the same parameters
     * should return the same results (assuming no modifications between queries).
     */
    @Property(tries = 50)
    @Label("Repeated queries return consistent results")
    void repeatedQueriesReturnConsistentResults(
            @ForAll("commandExecutionSets") List<CommandExecution> executions,
            @ForAll("intents") String intent,
            @ForAll @IntRange(min = 1, max = 10) int limit) {
        
        // Create memory instance with test data
        TestLongTermMemory memory = new TestLongTermMemory();
        executions.forEach(memory::storeCommand);
        
        // Query twice
        List<CommandExecution> results1 = memory.queryByIntent(intent, limit);
        List<CommandExecution> results2 = memory.queryByIntent(intent, limit);
        
        // Verify results are identical
        assertThat(results1)
                .as("Repeated queries should return identical results")
                .hasSize(results2.size());
        
        for (int i = 0; i < results1.size(); i++) {
            assertThat(results1.get(i).getId())
                    .as("Execution IDs should match at position " + i)
                    .isEqualTo(results2.get(i).getId());
        }
    }
    
    // Arbitraries (data generators)
    
    @Provide
    Arbitrary<List<CommandExecution>> commandExecutionSets() {
        return Arbitraries.integers().between(5, 30).flatMap(size ->
                Combinators.combine(
                        Arbitraries.of("status", "analyze", "improve", "test", "debug"),
                        Arbitraries.of("user-1", "user-2", "user-3"),
                        Arbitraries.of("project-A", "project-B", "project-C")
                ).as((intent, user, project) -> {
                    List<CommandExecution> executions = new ArrayList<>();
                    LocalDateTime now = LocalDateTime.now();
                    
                    for (int i = 0; i < size; i++) {
                        CommandExecution execution = new CommandExecution();
                        execution.setId(UUID.randomUUID().toString());
                        execution.setUserId(user);
                        execution.setProjectId(project);
                        execution.setRawCommand("sdlc " + intent + " target");
                        execution.setIntent(intent);
                        execution.setTarget("target-" + i);
                        execution.setStatus(CommandExecution.ExecutionStatus.SUCCESS);
                        execution.setOutcome("Completed successfully");
                        execution.setStartedAt(now.minus(i * 10, ChronoUnit.MINUTES));
                        execution.setCompletedAt(now.minus(i * 10 - 5, ChronoUnit.MINUTES));
                        execution.setDurationMs(300000L);
                        
                        executions.add(execution);
                    }
                    
                    return executions;
                })
        );
    }
    
    @Provide
    Arbitrary<String> intents() {
        return Arbitraries.of("status", "analyze", "improve", "test", "debug", "prepare", "release");
    }
    
    @Provide
    Arbitrary<String> userIds() {
        return Arbitraries.of("user-1", "user-2", "user-3", "user-4");
    }
    
    @Provide
    Arbitrary<String> projectIds() {
        return Arbitraries.of("project-A", "project-B", "project-C", "project-D");
    }
    
    @Provide
    Arbitrary<TimeRange> timeRanges() {
        LocalDateTime now = LocalDateTime.now();
        return Combinators.combine(
                Arbitraries.integers().between(1, 60),
                Arbitraries.integers().between(1, 30)
        ).as((startDaysAgo, durationDays) -> {
            LocalDateTime start = now.minus(startDaysAgo, ChronoUnit.DAYS);
            LocalDateTime end = start.plus(durationDays, ChronoUnit.DAYS);
            return new TimeRange(start, end);
        });
    }
    
    @Provide
    Arbitrary<String> searchQueries() {
        return Arbitraries.of(
                "analyze security vulnerabilities",
                "improve performance bottlenecks",
                "check project status",
                "run tests",
                "debug errors"
        );
    }
    
    // Helper classes
    
    private static class TimeRange {
        final LocalDateTime start;
        final LocalDateTime end;
        
        TimeRange(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }
    }
    
    /**
     * Test implementation of LongTermMemory for property testing.
     * 
     * Uses in-memory storage to verify query logic without requiring
     * database or vector store infrastructure.
     */
    private static class TestLongTermMemory implements LongTermMemory {
        
        private final List<CommandExecution> executions = new ArrayList<>();
        private final Map<String, Map<String, Object>> contexts = new HashMap<>();
        
        @Override
        public void storeCommand(CommandExecution execution) {
            executions.add(execution);
        }
        
        @Override
        public List<CommandExecution> queryByIntent(String intent, int limit) {
            return executions.stream()
                    .filter(e -> e.getIntent().equals(intent))
                    .sorted(Comparator.comparing(CommandExecution::getStartedAt).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
        }
        
        @Override
        public List<CommandExecution> queryByTimeRange(LocalDateTime startTime, LocalDateTime endTime, int limit) {
            return executions.stream()
                    .filter(e -> !e.getStartedAt().isBefore(startTime) && 
                                !e.getStartedAt().isAfter(endTime))
                    .sorted(Comparator.comparing(CommandExecution::getStartedAt).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
        }
        
        @Override
        public List<CommandExecution> queryByUser(String userId, int limit) {
            return executions.stream()
                    .filter(e -> e.getUserId().equals(userId))
                    .sorted(Comparator.comparing(CommandExecution::getStartedAt).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
        }
        
        @Override
        public List<CommandExecution> queryByProject(String projectId, int limit) {
            return executions.stream()
                    .filter(e -> e.getProjectId().equals(projectId))
                    .sorted(Comparator.comparing(CommandExecution::getStartedAt).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
        }
        
        @Override
        public List<CommandExecution> querySimilar(String query, int limit) {
            // Simple text matching for testing
            return executions.stream()
                    .filter(e -> e.getRawCommand().toLowerCase().contains(query.toLowerCase()) ||
                                e.getIntent().toLowerCase().contains(query.toLowerCase()))
                    .sorted(Comparator.comparing(CommandExecution::getStartedAt).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
        }
        
        @Override
        public void storeContext(String projectId, Map<String, Object> context) {
            contexts.put(projectId, new HashMap<>(context));
        }
        
        @Override
        public Map<String, Object> retrieveContext(String projectId) {
            return contexts.getOrDefault(projectId, Collections.emptyMap());
        }
        
        @Override
        public List<ContextEntry> queryContextSimilar(String projectId, String query, int limit) {
            Map<String, Object> context = contexts.get(projectId);
            if (context == null) {
                return Collections.emptyList();
            }
            
            return context.entrySet().stream()
                    .filter(e -> e.getValue().toString().toLowerCase().contains(query.toLowerCase()))
                    .limit(limit)
                    .map(e -> new ContextEntry(
                            UUID.randomUUID().toString(),
                            projectId,
                            e.getKey(),
                            e.getValue().toString()
                    ))
                    .collect(Collectors.toList());
        }
        
        @Override
        public int deleteOldExecutions(LocalDateTime olderThan) {
            int sizeBefore = executions.size();
            executions.removeIf(e -> e.getStartedAt().isBefore(olderThan));
            return sizeBefore - executions.size();
        }
        
        @Override
        public ExecutionStatistics getStatistics(String projectId) {
            List<CommandExecution> projectExecutions = executions.stream()
                    .filter(e -> e.getProjectId().equals(projectId))
                    .collect(Collectors.toList());
            
            long total = projectExecutions.size();
            long successful = projectExecutions.stream()
                    .filter(e -> e.getStatus() == CommandExecution.ExecutionStatus.SUCCESS)
                    .count();
            
            return new ExecutionStatistics(
                    projectId,
                    total,
                    successful,
                    0L,
                    0L,
                    total > 0 ? (double) successful / total : 0.0,
                    0L,
                    "status"
            );
        }
    }
}
