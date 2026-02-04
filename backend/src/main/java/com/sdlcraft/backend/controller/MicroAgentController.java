package com.sdlcraft.backend.controller;

import com.sdlcraft.backend.integration.qa.MicroAgentService;
import com.sdlcraft.backend.integration.qa.MicroAgentService.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * REST API for MicroAgent functionality.
 * Exposes the micro-agent test-driven code generation via HTTP.
 */
@RestController
@RequestMapping("/api/agent")
public class MicroAgentController {

    private final MicroAgentService microAgentService;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    
    // Store active sessions for resume capability (like micro-agent's thread ID)
    private final Map<String, RunOptions> activeSessions = new HashMap<>();

    public MicroAgentController(MicroAgentService microAgentService) {
        this.microAgentService = microAgentService;
    }

    /**
     * Request body for running the agent
     */
    public record AgentRunRequest(
        String outputFile,      // File to create/modify
        String promptFile,      // .prompt.md file (optional)
        String testCommand,     // e.g., "npm test"
        String testFile,        // Test file path
        int maxRuns,            // Max iterations (default 20)
        boolean interactive,    // Interactive mode
        String threadId         // For resuming
    ) {}

    /**
     * Response with status and results
     */
    public record AgentRunResponse(
        boolean success,
        String message,
        String generatedCode,
        List<String> iterations,
        String threadId
    ) {}

    /**
     * Run micro-agent synchronously (for simple use cases)
     */
    @PostMapping("/run")
    public AgentRunResponse run(@RequestBody AgentRunRequest request) {
        String threadId = request.threadId != null && !request.threadId.isEmpty() 
            ? request.threadId 
            : UUID.randomUUID().toString();
        
        RunOptions options = new RunOptions(
            request.outputFile,
            request.promptFile != null ? request.promptFile : "",
            request.testCommand,
            request.testFile,
            "",  // lastRunError
            "",  // priorCode
            threadId,
            request.maxRuns > 0 ? request.maxRuns : 20,
            request.interactive,
            false // addedLogs
        );
        
        // Store session for resume
        activeSessions.put(threadId, options);
        
        List<String> iterations = new ArrayList<>();
        List<RunResult> results = microAgentService.runAll(options, msg -> iterations.add(msg));
        
        // Check final result
        boolean success = false;
        String generatedCode = "";
        
        if (!results.isEmpty()) {
            RunResult lastResult = results.get(results.size() - 1);
            success = lastResult.testResult() instanceof TestResult.Success;
            generatedCode = lastResult.code();
        }
        
        return new AgentRunResponse(
            success,
            success ? "All tests passed!" : "Max runs reached or error occurred",
            generatedCode,
            iterations,
            threadId
        );
    }

    /**
     * Run micro-agent with Server-Sent Events for real-time streaming
     * This matches micro-agent's streaming output pattern
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
        @RequestParam String outputFile,
        @RequestParam String testCommand,
        @RequestParam String testFile,
        @RequestParam(required = false) String promptFile,
        @RequestParam(defaultValue = "20") int maxRuns,
        @RequestParam(required = false) String threadId
    ) {
        SseEmitter emitter = new SseEmitter(300000L); // 5 minute timeout
        
        String sessionId = threadId != null && !threadId.isEmpty() 
            ? threadId 
            : UUID.randomUUID().toString();
        
        RunOptions options = new RunOptions(
            outputFile,
            promptFile != null ? promptFile : "",
            testCommand,
            testFile,
            "",
            "",
            sessionId,
            maxRuns,
            false,
            false
        );
        
        activeSessions.put(sessionId, options);
        
        executor.submit(() -> {
            try {
                // Send session ID first
                emitter.send(SseEmitter.event()
                    .name("session")
                    .data(Map.of("threadId", sessionId)));
                
                microAgentService.runAll(options, msg -> {
                    try {
                        emitter.send(SseEmitter.event()
                            .name("output")
                            .data(msg));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                
                emitter.send(SseEmitter.event()
                    .name("complete")
                    .data(Map.of("success", true)));
                emitter.complete();
                
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of("message", e.getMessage())));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });
        
        return emitter;
    }

    /**
     * Generate code only (without running tests) - for one-shot generation
     */
    @PostMapping("/generate")
    public Map<String, Object> generate(@RequestBody AgentRunRequest request) {
        RunOptions options = new RunOptions(
            request.outputFile,
            request.promptFile != null ? request.promptFile : "",
            request.testCommand,
            request.testFile,
            "",
            "",
            "",
            1,
            request.interactive,
            false
        );
        
        String code = microAgentService.generate(options, msg -> {});
        String cleanCode = microAgentService.removeBackticks(code);
        
        return Map.of(
            "success", true,
            "code", cleanCode,
            "rawOutput", code
        );
    }

    /**
     * Generate test file - matches micro-agent's interactive test generation
     */
    @PostMapping("/generate-test")
    public Map<String, Object> generateTest(@RequestBody GenerateTestRequest request) {
        String testCode = microAgentService.generateTest(
            request.prompt,
            request.outputFile,
            request.testFile,
            request.exampleTests != null ? request.exampleTests : List.of()
        );
        
        return Map.of(
            "success", true,
            "testCode", testCode
        );
    }

    public record GenerateTestRequest(
        String prompt,
        String outputFile,
        String testFile,
        List<String> exampleTests
    ) {}

    /**
     * Resume a previous session
     */
    @PostMapping("/resume/{threadId}")
    public AgentRunResponse resume(@PathVariable String threadId) {
        RunOptions options = activeSessions.get(threadId);
        
        if (options == null) {
            return new AgentRunResponse(
                false,
                "Session not found: " + threadId,
                "",
                List.of(),
                threadId
            );
        }
        
        List<String> iterations = new ArrayList<>();
        List<RunResult> results = microAgentService.runAll(options, msg -> iterations.add(msg));
        
        boolean success = !results.isEmpty() && 
            results.get(results.size() - 1).testResult() instanceof TestResult.Success;
        
        return new AgentRunResponse(
            success,
            success ? "All tests passed!" : "Max runs reached",
            !results.isEmpty() ? results.get(results.size() - 1).code() : "",
            iterations,
            threadId
        );
    }

    /**
     * Get resume command for a session
     */
    @GetMapping("/command/{threadId}")
    public Map<String, String> getCommand(@PathVariable String threadId) {
        RunOptions options = activeSessions.get(threadId);
        
        if (options == null) {
            return Map.of("error", "Session not found");
        }
        
        return Map.of(
            "command", microAgentService.createCommandString(options)
        );
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "ok",
            "activeSessions", activeSessions.size()
        );
    }
}

