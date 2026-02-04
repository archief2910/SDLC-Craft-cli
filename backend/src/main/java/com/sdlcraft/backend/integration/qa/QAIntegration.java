package com.sdlcraft.backend.integration.qa;

import com.sdlcraft.backend.integration.Integration;
import com.sdlcraft.backend.integration.IntegrationHealth;
import com.sdlcraft.backend.integration.IntegrationResult;
import com.sdlcraft.backend.integration.qa.MicroAgentService.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * QA Integration - Wraps MicroAgentService for SDLC integration.
 * Provides the micro-agent pattern for test-driven code generation.
 */
@Component
public class QAIntegration implements Integration {
    
    private static final Logger logger = LoggerFactory.getLogger(QAIntegration.class);
    
    private final MicroAgentService microAgentService;
    
    @Value("${sdlcraft.integration.qa.enabled:true}")
    private boolean enabled;
    
    public QAIntegration(MicroAgentService microAgentService) {
        this.microAgentService = microAgentService;
    }
    
    @Override
    public String getId() {
        return "qa";
    }
    
    @Override
    public String getName() {
        return "QA & Testing (micro-agent)";
    }
    
    @Override
    public boolean isConfigured() {
        return enabled;
    }
    
    @Override
    public String[] getSupportedActions() {
        return new String[]{
            "runAgent",
            "generate",
            "generateTest",
            "runTests"
        };
    }
    
    @Override
    public IntegrationHealth healthCheck() {
        long start = System.currentTimeMillis();
        if (!enabled) {
            return IntegrationHealth.unhealthy("qa", "QA integration is disabled");
        }
        long elapsed = System.currentTimeMillis() - start;
        return IntegrationHealth.healthy("qa", elapsed);
    }
    
    /**
     * Run the micro-agent - iterative test-driven code generation
     * This is the main entry point matching micro-agent's behavior
     */
    public IntegrationResult runAgent(String outputFile, String testFile, String testCommand, int maxRuns) {
        long start = System.currentTimeMillis();
        
        if (outputFile == null || outputFile.isEmpty()) {
            return IntegrationResult.failure("qa", "runAgent", "outputFile is required");
        }
        if (testCommand == null || testCommand.isEmpty()) {
            return IntegrationResult.failure("qa", "runAgent", "testCommand is required");
        }
        if (testFile == null || testFile.isEmpty()) {
            return IntegrationResult.failure("qa", "runAgent", "testFile is required");
        }
        
        RunOptions options = new RunOptions(
            outputFile,
            "",  // promptFile
            testCommand,
            testFile,
            "",  // lastRunError
            "",  // priorCode
            "",  // threadId
            maxRuns > 0 ? maxRuns : 20,
            false,  // interactive
            false   // addedLogs
        );
        
        List<String> output = new ArrayList<>();
        List<RunResult> results = microAgentService.runAll(options, msg -> {
            output.add(msg);
            logger.info(msg);
        });
        
        boolean success = !results.isEmpty() && 
            results.get(results.size() - 1).testResult() instanceof TestResult.Success;
        
        String generatedCode = !results.isEmpty() ? results.get(results.size() - 1).code() : "";
        
        long elapsed = System.currentTimeMillis() - start;
        
        return new IntegrationResult(
            "qa",
            "runAgent",
            success,
            success ? "All tests passed!" : "Max runs reached or tests still failing",
            Map.of(
                "code", generatedCode,
                "iterations", results.size(),
                "output", output
            ),
            elapsed
        );
    }
    
    /**
     * Generate code only (one-shot, no test loop)
     */
    public IntegrationResult generate(String outputFile, String testFile, String promptFile) {
        long start = System.currentTimeMillis();
        
        RunOptions options = new RunOptions(
            outputFile,
            promptFile != null ? promptFile : "",
            "",
            testFile,
            "",
            "",
            "",
            1,
            false,
            false
        );
        
        String code = microAgentService.generate(options, msg -> {});
        String cleanCode = microAgentService.removeBackticks(code);
        
        long elapsed = System.currentTimeMillis() - start;
        
        return IntegrationResult.success(
            "qa",
            "generate",
            "Code generated successfully",
            Map.of("code", cleanCode, "rawOutput", code),
            elapsed
        );
    }
    
    /**
     * Generate test file
     */
    public IntegrationResult generateTest(String prompt, String outputFile, String testFile) {
        long start = System.currentTimeMillis();
        
        String testCode = microAgentService.generateTest(prompt, outputFile, testFile, List.of());
        
        long elapsed = System.currentTimeMillis() - start;
        
        return IntegrationResult.success(
            "qa",
            "generateTest",
            "Test generated successfully",
            Map.of("testCode", testCode),
            elapsed
        );
    }
    
    /**
     * Run tests and return result (no AI iteration)
     */
    public IntegrationResult runTests(String workingDir, String testCommand) {
        long start = System.currentTimeMillis();
        
        RunOptions options = new RunOptions(
            workingDir,
            "",
            testCommand,
            "",
            "",
            "",
            "",
            1,
            false,
            false
        );
        
        List<String> output = new ArrayList<>();
        TestResult result = microAgentService.test(options, msg -> output.add(msg));
        
        boolean success = result instanceof TestResult.Success;
        String message = success ? "Tests passed" : 
            (result instanceof TestResult.Fail f ? f.message() : "Unknown error");
        
        long elapsed = System.currentTimeMillis() - start;
        
        return new IntegrationResult(
            "qa",
            "runTests",
            success,
            message,
            Map.of("output", output),
            elapsed
        );
    }
    
    // Legacy method signatures for backward compatibility
    
    public IntegrationResult runTestFile(String workingDir, String testFile) {
        return runTests(workingDir, testFile);
    }
    
    public IntegrationResult generateTests(String codeFile, String testFile) {
        return generateTest("Generate tests for the code", codeFile, testFile);
    }
    
    public IntegrationResult iterativeTestFix(String codeFile, String testFile, String workingDir, String testCommand) {
        return runAgent(codeFile, testFile, testCommand, 20);
    }
    
    public IntegrationResult analyzeCoverage(String workingDir) {
        return runTests(workingDir, "npm run coverage");
    }
}
