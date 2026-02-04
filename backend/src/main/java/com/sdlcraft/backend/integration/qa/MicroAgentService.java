package com.sdlcraft.backend.integration.qa;

import com.sdlcraft.backend.llm.LLMProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * MicroAgentService - Exact implementation of micro-agent's test-driven code generation.
 * 
 * This follows the micro-agent pattern precisely:
 * 1. Run tests first
 * 2. If fail: Generate/fix code with LLM using structured prompts
 * 3. Write code to file
 * 4. Run tests again
 * 5. Repeat until success or max runs (default 20)
 * 
 * Special features from micro-agent:
 * - Detect repeated failures (same error 4 times)
 * - Auto-add debug logs when stuck
 * - Remove logs after success
 * - Resume capability with state persistence
 */
@Service
public class MicroAgentService {

    private static final Logger logger = LoggerFactory.getLogger(MicroAgentService.class);

    // Exact system prompt from micro-agent
    public static final String SYSTEM_PROMPT = """
        You take a prompt and existing unit tests and generate the function implementation accordingly.

        1. Think step by step about the algorithm, reasoning about the problem and the solution, similar algorithm, the state, data structures and strategy you will use. Explain all that without emitting any code in this step.

        2. Emit a markdown code block with production-ready generated code (function that satisfies all the tests and the prompt).
         - Be sure your code exports function that can be called by an external test file.
         - Make sure your code is reusable and not overly hardcoded to match the prompt.
         - Use two spaces for indents. Add logs if helpful for debugging, you will get the log output on your next try to help you debug.
         - Always return a complete code snippet that can execute, nothing partial and never say "rest of your code" or similar, I will copy and paste your code into my file without modification, so it cannot have gaps or parts where you say to put the "rest of the code" back in.
         - Do not emit tests, just the function implementation.

        Stop emitting after the code block""";

    private final LLMProvider llmProvider;
    
    @Value("${sdlcraft.integration.qa.max-iterations:20}")
    private int defaultMaxRuns;
    
    @Value("${sdlcraft.integration.qa.test-timeout-seconds:20}")
    private int testTimeoutSeconds;

    // Track previous failures to detect repeated errors (micro-agent pattern)
    private final List<String> prevTestFailures = new ArrayList<>();

    public MicroAgentService(LLMProvider llmProvider) {
        this.llmProvider = llmProvider;
    }

    /**
     * Run options matching micro-agent's RunOptions type
     */
    public record RunOptions(
        String outputFile,      // File to generate/modify
        String promptFile,      // .prompt.md file with requirements
        String testCommand,     // Command to run tests
        String testFile,        // Test file path
        String lastRunError,    // Error from last run
        String priorCode,       // Previous code version
        String threadId,        // For resume capability
        int maxRuns,            // Max iterations (default 20)
        boolean interactive,    // Interactive mode
        boolean addedLogs       // Whether debug logs were added
    ) {
        public RunOptions withLastRunError(String error) {
            return new RunOptions(outputFile, promptFile, testCommand, testFile, error, priorCode, threadId, maxRuns, interactive, addedLogs);
        }
        public RunOptions withAddedLogs(boolean added) {
            return new RunOptions(outputFile, promptFile, testCommand, testFile, lastRunError, priorCode, threadId, maxRuns, interactive, added);
        }
        public RunOptions withPriorCode(String code) {
            return new RunOptions(outputFile, promptFile, testCommand, testFile, lastRunError, code, threadId, maxRuns, interactive, addedLogs);
        }
    }

    /**
     * Test result - matching micro-agent's Result type
     */
    public sealed interface TestResult permits TestResult.Success, TestResult.Fail {
        record Success() implements TestResult {}
        record Fail(String message) implements TestResult {}
    }

    /**
     * Run result from one iteration
     */
    public record RunResult(String code, TestResult testResult) {}

    /**
     * Main entry point - matches micro-agent's runAll function
     */
    public List<RunResult> runAll(RunOptions options, Consumer<String> onOutput) {
        onOutput.accept("🦾 Micro Agent\n");
        
        List<RunResult> results = new ArrayList<>();
        
        // Run tests first (micro-agent pattern)
        if (options.outputFile != null && !options.outputFile.isEmpty()) {
            onOutput.accept("Running tests...\n");
            TestResult initialTest = test(options, onOutput);
            
            if (initialTest instanceof TestResult.Success) {
                // If logs were added previously, remove them
                if (options.addedLogs) {
                    try {
                        String codeWithoutLogs = removeLogsFromCode(options);
                        Files.writeString(Path.of(options.outputFile), codeWithoutLogs);
                        options = options.withAddedLogs(false);
                    } catch (Exception e) {
                        logger.error("Failed to remove logs", e);
                    }
                }
                onOutput.accept("✅ All tests passed!\n");
                return results;
            }
            
            options = options.withLastRunError(
                initialTest instanceof TestResult.Fail f ? f.message() : ""
            );
        }
        
        // Main iteration loop - matches micro-agent's run generator
        for (RunResult result : run(options, onOutput)) {
            results.add(result);
        }
        
        return results;
    }

    /**
     * Generator-style iteration loop - matches micro-agent's run function
     */
    public Iterable<RunResult> run(RunOptions options, Consumer<String> onOutput) {
        return () -> new Iterator<>() {
            private int iteration = 0;
            private final int maxRuns = options.maxRuns > 0 ? options.maxRuns : defaultMaxRuns;
            private boolean passed = false;
            private RunOptions currentOptions = options;

            @Override
            public boolean hasNext() {
                return iteration < maxRuns && !passed;
            }

            @Override
            public RunResult next() {
                iteration++;
                onOutput.accept(String.format("Iteration %d/%d\n", iteration, maxRuns));
                
                RunResult result = runOne(currentOptions, onOutput);
                
                if (result.testResult() instanceof TestResult.Success) {
                    onOutput.accept("✅ All tests passed!\n");
                    passed = true;
                } else if (result.testResult() instanceof TestResult.Fail f) {
                    currentOptions = currentOptions.withLastRunError(f.message());
                }
                
                if (!passed && iteration >= maxRuns) {
                    onOutput.accept(String.format("⚠️ Max runs of %d reached.\n", maxRuns));
                    onOutput.accept(String.format("Resume command: sdlc agent %s -t \"%s\" -f %s -m %d\n",
                        currentOptions.outputFile,
                        currentOptions.testCommand,
                        currentOptions.testFile,
                        maxRuns
                    ));
                }
                
                return result;
            }
        };
    }

    /**
     * Single iteration - matches micro-agent's runOne function
     */
    public RunResult runOne(RunOptions options, Consumer<String> onOutput) {
        onOutput.accept("Generating code...\n");
        
        // Generate code using LLM
        String generatedCode = generate(options, onOutput);
        generatedCode = removeBackticks(generatedCode);
        
        // Write to output file
        try {
            Files.writeString(Path.of(options.outputFile), generatedCode);
            onOutput.accept("Updated code\n");
        } catch (IOException e) {
            logger.error("Failed to write output file", e);
            return new RunResult(generatedCode, new TestResult.Fail("Failed to write file: " + e.getMessage()));
        }
        
        // Run tests
        onOutput.accept("Running tests...\n");
        TestResult testResult = test(options, onOutput);
        
        return new RunResult(generatedCode, testResult);
    }

    /**
     * Generate code - matches micro-agent's generate function exactly
     */
    public String generate(RunOptions options, Consumer<String> onOutput) {
        String prompt = readFileOrDefault(options.promptFile, "");
        String priorCode = readFileOrDefault(options.outputFile, "");
        String testCode = readFileOrDefault(options.testFile, "");
        String packageJson = readFileOrDefault("package.json", "");
        
        // Build user prompt exactly like micro-agent
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Here is what I need:\n\n");
        userPrompt.append("<prompt>\n");
        userPrompt.append(prompt.isEmpty() ? "Pass the tests" : prompt);
        userPrompt.append("\n</prompt>\n\n");
        
        userPrompt.append("The current code is:\n");
        userPrompt.append("<code>\n");
        userPrompt.append(priorCode.isEmpty() ? "None" : priorCode);
        userPrompt.append("\n</code>\n\n");
        
        userPrompt.append("The file path for the above is ").append(options.outputFile).append(".\n\n");
        
        userPrompt.append("The test code that needs to pass is:\n");
        userPrompt.append("<test>\n");
        userPrompt.append(testCode);
        userPrompt.append("\n</test>\n\n");
        
        userPrompt.append("The file path for the test is ").append(options.testFile).append(".\n\n");
        
        userPrompt.append("The error you received on that code was:\n");
        userPrompt.append("<error>\n");
        userPrompt.append(options.lastRunError.isEmpty() ? "None" : options.lastRunError);
        userPrompt.append("\n</error>\n\n");
        
        if (!packageJson.isEmpty()) {
            userPrompt.append("Don't use any node modules that aren't included here unless specifically told otherwise:\n");
            userPrompt.append("<package-json>\n");
            userPrompt.append(packageJson);
            userPrompt.append("\n</package-json>\n\n");
        }
        
        userPrompt.append("Please update the code (or generate all new code if needed) to satisfy the prompt and test.\n\n");
        userPrompt.append("Be sure to use good coding conventions. For instance, if you are generating a typescript\n");
        userPrompt.append("file, use types (e.g. for function parameters, etc).\n\n");
        
        if (!options.interactive) {
            userPrompt.append("If there is already existing code, strictly maintain the same coding style as the existing code.\n");
            userPrompt.append("Any updated code should look like its written by the same person/team that wrote the original code.\n");
        }
        
        logger.debug("User prompt:\n{}", userPrompt);
        
        // Call LLM with system prompt + user prompt
        return llmProvider.complete(
            SYSTEM_PROMPT + "\n\nUser:\n" + userPrompt,
            Map.of("temperature", 0.7, "max_tokens", 4000)
        );
    }

    /**
     * Run tests - matches micro-agent's test function
     */
    public TestResult test(RunOptions options, Consumer<String> onOutput) {
        if (options.testCommand == null || options.testCommand.isEmpty()) {
            return new TestResult.Fail("No test command specified");
        }
        
        try {
            ProcessBuilder pb = new ProcessBuilder();
            String os = System.getProperty("os.name").toLowerCase();
            
            if (os.contains("windows")) {
                pb.command("cmd.exe", "/c", options.testCommand);
            } else {
                pb.command("sh", "-c", options.testCommand);
            }
            
            // Set working directory to the directory containing the output file
            Path outputPath = Path.of(options.outputFile);
            if (outputPath.getParent() != null) {
                pb.directory(outputPath.getParent().toFile());
            }
            
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            
            // Stream output in real-time (micro-agent pattern)
            String line;
            long lastOutputTime = System.currentTimeMillis();
            
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                onOutput.accept("│   " + line + "\n");
                lastOutputTime = System.currentTimeMillis();
                
                // Check for timeout (micro-agent uses 20 seconds)
                if (System.currentTimeMillis() - lastOutputTime > testTimeoutSeconds * 1000) {
                    process.destroyForcibly();
                    return new TestResult.Fail("Test timeout - no output for " + testTimeoutSeconds + " seconds. Is your test in watch mode?");
                }
            }
            
            boolean finished = process.waitFor(testTimeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new TestResult.Fail("Test timeout after " + testTimeoutSeconds + " seconds");
            }
            
            int exitCode = process.exitValue();
            String outputStr = output.toString();
            
            // Check for invalid command (micro-agent pattern)
            if (isInvalidCommand(outputStr)) {
                return new TestResult.Fail("Invalid test command: " + outputStr);
            }
            
            if (exitCode != 0) {
                return testFail(outputStr, options);
            }
            
            return new TestResult.Success();
            
        } catch (Exception e) {
            logger.error("Test execution failed", e);
            return testFail(e.getMessage(), options);
        }
    }

    /**
     * Handle test failure - matches micro-agent's testFail function
     * Detects repeated failures and triggers log addition
     */
    private TestResult testFail(String message, RunOptions options) {
        prevTestFailures.add(message);
        
        // Check if same error 4 times in a row (micro-agent pattern)
        if (hasFailedNTimesWithSameMessage(message, 4)) {
            if (!options.addedLogs) {
                // Trigger adding logs
                return new TestResult.Fail("Repeated test failures detected. Adding logs to the code.");
            } else {
                // Already added logs and still failing
                return new TestResult.Fail(
                    "Your test command is failing with the same error several times. " +
                    "Please make sure your test command is correct. Message: " + truncate(message, 500)
                );
            }
        }
        
        return new TestResult.Fail(message);
    }

    /**
     * Check for repeated failures - exact micro-agent implementation
     */
    private boolean hasFailedNTimesWithSameMessage(String message, int n) {
        if (prevTestFailures.size() < n) {
            return false;
        }
        
        // Check if last n failures have same message
        int start = prevTestFailures.size() - n;
        for (int i = start; i < prevTestFailures.size(); i++) {
            if (!prevTestFailures.get(i).equals(message)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check for invalid command - micro-agent pattern
     */
    private boolean isInvalidCommand(String output) {
        return output.contains("command not found:") ||
               output.contains("command_not_found:") ||
               output.contains("npm ERR! Missing script:");
    }

    /**
     * Add logs to code for debugging - micro-agent pattern
     */
    public String addLogsToCode(RunOptions options) {
        String prompt = String.format("""
            Please add detailed logs to the following code to help debug repeated test failures:
            
            <code>%s</code>
            
            The error you received on that code was:
            
            <error>%s</error>
            """, options.priorCode, options.lastRunError);
        
        return llmProvider.complete(
            "You are an assistant that helps improve code by adding logs for debugging.\n\n" + prompt,
            Map.of()
        );
    }

    /**
     * Remove logs from code - micro-agent pattern
     */
    public String removeLogsFromCode(RunOptions options) {
        String prompt = String.format("Please remove all logs from the following code:\n\n%s", options.priorCode);
        
        return llmProvider.complete(
            "You are an assistant that helps clean up code by removing logs.\n\n" + prompt,
            Map.of()
        );
    }

    /**
     * Extract code from markdown code block - micro-agent's getCodeBlock
     */
    public String removeBackticks(String output) {
        int foundCode = output.indexOf("```");
        if (foundCode == -1) {
            return output;
        }
        
        int start = output.indexOf("\n", foundCode);
        if (start == -1) {
            return output.substring(foundCode);
        }
        
        int end = output.indexOf("```", start);
        if (end == -1) {
            logger.warn("Code block end not found");
            return output.substring(start).trim();
        }
        
        return output.substring(start, end).trim();
    }

    /**
     * Generate test file - matches micro-agent's interactive mode test generation
     */
    public String generateTest(String prompt, String outputFile, String testFile, List<String> exampleTests) {
        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append("""
            You are an AI assistant that given a user prompt, returns a markdown for a unit test.
            1. Think step by step before emiting any code. Think about the shape of the input and output, the behavior and special situations that are relevant to the algorithm.

            2. After planning, return a code block with the test code.
              - Start with the most basic test case and progress to more complex ones.
              - Start with the happy path, then edge cases.
              - Inputs that are invalid, and likely to break the algorithm.
              - Keep the individual tests small and focused.
              - Focus in behavior, not implementation.

              Stop emitting after the code block.
            """);
        
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Please prepare a unit test file (can be multiple tests) for the following prompt:\n");
        userPrompt.append("<prompt>\n").append(prompt).append("\n</prompt>\n\n");
        userPrompt.append("The test will be located at `").append(testFile).append("` and the code to test will be located at\n");
        userPrompt.append("`").append(outputFile).append("`.\n\n");
        
        if (!exampleTests.isEmpty()) {
            userPrompt.append("Here is a copy of a couple example tests in the repo:\n");
            userPrompt.append("<tests>\n");
            for (String test : exampleTests) {
                userPrompt.append(test).append("\n");
            }
            userPrompt.append("</tests>\n\n");
        }
        
        userPrompt.append("Only output the test code. No other words, just the code.\n");
        
        String result = llmProvider.complete(
            systemPrompt + "\n\nUser:\n" + userPrompt,
            Map.of("temperature", 0.7, "max_tokens", 4000)
        );
        
        return removeBackticks(result);
    }

    /**
     * Create command string for resuming - micro-agent pattern
     */
    public String createCommandString(RunOptions options) {
        StringBuilder cmd = new StringBuilder("sdlc agent");
        
        if (options.outputFile != null) {
            cmd.append(" ").append(options.outputFile);
        }
        if (options.promptFile != null && !options.promptFile.isEmpty()) {
            cmd.append(" -p ").append(options.promptFile);
        }
        if (options.testCommand != null && !options.testCommand.isEmpty()) {
            cmd.append(" -t \"").append(options.testCommand.replace("\"", "\\\"")).append("\"");
        }
        if (options.testFile != null && !options.testFile.isEmpty()) {
            cmd.append(" -f ").append(options.testFile);
        }
        if (options.maxRuns > 0) {
            cmd.append(" -m ").append(options.maxRuns);
        }
        if (options.threadId != null && !options.threadId.isEmpty()) {
            cmd.append(" --thread ").append(options.threadId);
        }
        
        return cmd.toString();
    }

    private String readFileOrDefault(String path, String defaultValue) {
        if (path == null || path.isEmpty()) return defaultValue;
        try {
            return Files.readString(Path.of(path));
        } catch (IOException e) {
            return defaultValue;
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}

