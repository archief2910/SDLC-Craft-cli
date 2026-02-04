package cmd

import (
	"bufio"
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"strings"
	"syscall"

	"github.com/fatih/color"
	"github.com/spf13/cobra"
)

// Agent command flags - matching micro-agent's CLI flags exactly
var (
	agentPrompt   string
	agentTest     string
	agentTestFile string
	agentMaxRuns  int
	agentThread   string
	agentVisual   string
)

// AgentRunRequest matches the backend API
type AgentRunRequest struct {
	OutputFile  string `json:"outputFile"`
	PromptFile  string `json:"promptFile"`
	TestCommand string `json:"testCommand"`
	TestFile    string `json:"testFile"`
	MaxRuns     int    `json:"maxRuns"`
	Interactive bool   `json:"interactive"`
	ThreadID    string `json:"threadId"`
}

// AgentRunResponse matches the backend API
type AgentRunResponse struct {
	Success       bool     `json:"success"`
	Message       string   `json:"message"`
	GeneratedCode string   `json:"generatedCode"`
	Iterations    []string `json:"iterations"`
	ThreadID      string   `json:"threadId"`
}

// agentCmd represents the micro-agent style command
// Usage: sdlc agent [file path] -t "test command" -f test.file
var agentCmd = &cobra.Command{
	Use:   "agent [file path]",
	Short: "Run AI-powered test-driven code generation (micro-agent style)",
	Long: `🦾 Micro Agent - AI-powered test-driven code generation

This command implements the micro-agent pattern:
1. Run tests first
2. If fail: Generate/fix code using AI
3. Write code to file
4. Run tests again
5. Repeat until success or max runs (default 20)

Examples:
  # Interactive mode (prompts for everything)
  sdlc agent

  # Run with file and test command
  sdlc agent calculator.js -t "npm test"

  # Specify test file explicitly
  sdlc agent calculator.js -t "npm test" -f calculator.test.js

  # Set max iterations
  sdlc agent calculator.js -t "npm test" -m 10

  # Resume a previous session
  sdlc agent calculator.js -t "npm test" --thread abc123
`,
	Run: runAgent,
}

func init() {
	rootCmd.AddCommand(agentCmd)

	// Flags matching micro-agent's CLI exactly
	agentCmd.Flags().StringVarP(&agentPrompt, "prompt", "p", "", "Prompt file path or prompt text")
	agentCmd.Flags().StringVarP(&agentTest, "test", "t", "", "The test command to run (e.g., 'npm test')")
	agentCmd.Flags().StringVarP(&agentTestFile, "testFile", "f", "", "The test file path")
	agentCmd.Flags().IntVarP(&agentMaxRuns, "maxRuns", "m", 20, "Maximum number of iterations")
	agentCmd.Flags().StringVar(&agentThread, "thread", "", "Thread ID to resume a previous session")
	agentCmd.Flags().StringVarP(&agentVisual, "visual", "v", "", "Visual diff URL (for visual testing)")
}

func runAgent(cmd *cobra.Command, args []string) {
	// Color setup
	cyan := color.New(color.FgCyan).SprintFunc()
	red := color.New(color.FgRed).SprintFunc()

	// Handle SIGINT gracefully (like micro-agent)
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)
	go func() {
		<-sigChan
		fmt.Println()
		fmt.Println(red("✖") + " Stopping.")
		fmt.Println()
		os.Exit(0)
	}()

	fmt.Println()
	fmt.Println("🦾 " + cyan("Micro Agent"))
	fmt.Println()

	var filePath string
	if len(args) > 0 {
		filePath = args[0]
	}

	// Interactive mode if no file path or test command
	if filePath == "" || agentTest == "" {
		runInteractiveMode(filePath)
		return
	}

	// Derive test file if not specified (micro-agent pattern)
	testFile := agentTestFile
	if testFile == "" {
		testFile = deriveTestFile(filePath)
	}

	// Derive prompt file (micro-agent pattern: file.prompt.md)
	promptFile := agentPrompt
	if promptFile == "" {
		ext := filepath.Ext(filePath)
		promptFile = strings.TrimSuffix(filePath, ext) + ".prompt.md"
	}

	// Build request
	request := AgentRunRequest{
		OutputFile:  filePath,
		PromptFile:  promptFile,
		TestCommand: agentTest,
		TestFile:    testFile,
		MaxRuns:     agentMaxRuns,
		Interactive: false,
		ThreadID:    agentThread,
	}

	// Run with streaming
	runAgentStream(request)
}

func runInteractiveMode(filePath string) {
	cyan := color.New(color.FgCyan).SprintFunc()
	gray := color.New(color.FgHiBlack).SprintFunc()

	reader := bufio.NewReader(os.Stdin)

	// Get prompt
	fmt.Print(cyan("?") + " What would you like to do? " + gray("(A function that ...)") + "\n  ")
	prompt, _ := reader.ReadString('\n')
	prompt = strings.TrimSpace(prompt)

	if len(prompt) < 10 {
		fmt.Println(color.RedString("Please provide a complete prompt"))
		return
	}

	// Get file path if not provided
	if filePath == "" {
		fmt.Print(cyan("?") + " What file would you like to create or edit?\n  ")
		filePath, _ = reader.ReadString('\n')
		filePath = strings.TrimSpace(filePath)
	}

	// Derive test file
	testFile := deriveTestFile(filePath)
	fmt.Print(cyan("?") + " Test file " + gray("("+testFile+")") + "\n  ")
	customTestFile, _ := reader.ReadString('\n')
	customTestFile = strings.TrimSpace(customTestFile)
	if customTestFile != "" {
		testFile = customTestFile
	}

	// Get test command
	defaultTestCmd := guessTestCommand(filePath)
	fmt.Print(cyan("?") + " What command should I run to test the code? " + gray("("+defaultTestCmd+")") + "\n  ")
	testCmd, _ := reader.ReadString('\n')
	testCmd = strings.TrimSpace(testCmd)
	if testCmd == "" {
		testCmd = defaultTestCmd
	}

	fmt.Println()
	fmt.Println(cyan("ℹ") + " Agent running...")
	fmt.Println()

	// Create prompt file with the prompt
	ext := filepath.Ext(filePath)
	promptFile := strings.TrimSuffix(filePath, ext) + ".prompt.md"
	os.WriteFile(promptFile, []byte(prompt), 0644)

	request := AgentRunRequest{
		OutputFile:  filePath,
		PromptFile:  promptFile,
		TestCommand: testCmd,
		TestFile:    testFile,
		MaxRuns:     agentMaxRuns,
		Interactive: true,
		ThreadID:    "",
	}

	runAgentStream(request)
}

func runAgentStream(request AgentRunRequest) {
	backendURL := os.Getenv("SDLCRAFT_BACKEND_URL")
	if backendURL == "" {
		backendURL = "http://localhost:8080"
	}

	// Build URL with query params for SSE endpoint
	url := fmt.Sprintf("%s/api/agent/stream?outputFile=%s&testCommand=%s&testFile=%s&maxRuns=%d",
		backendURL,
		request.OutputFile,
		request.TestCommand,
		request.TestFile,
		request.MaxRuns,
	)

	if request.PromptFile != "" {
		url += "&promptFile=" + request.PromptFile
	}
	if request.ThreadID != "" {
		url += "&threadId=" + request.ThreadID
	}

	// Try SSE streaming first
	resp, err := http.Get(url)
	if err != nil {
		// Fall back to synchronous API
		runAgentSync(request)
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		// Fall back to synchronous API
		runAgentSync(request)
		return
	}

	// Read SSE stream
	reader := bufio.NewReader(resp.Body)
	for {
		line, err := reader.ReadString('\n')
		if err != nil {
			if err != io.EOF {
				fmt.Println(color.RedString("Stream error: %v", err))
			}
			break
		}

		line = strings.TrimSpace(line)
		if strings.HasPrefix(line, "data:") {
			data := strings.TrimPrefix(line, "data:")
			fmt.Print(data)
		}
	}
}

func runAgentSync(request AgentRunRequest) {
	backendURL := os.Getenv("SDLCRAFT_BACKEND_URL")
	if backendURL == "" {
		backendURL = "http://localhost:8080"
	}

	jsonBody, _ := json.Marshal(request)
	resp, err := http.Post(
		backendURL+"/api/agent/run",
		"application/json",
		bytes.NewBuffer(jsonBody),
	)

	if err != nil {
		fmt.Println(color.RedString("✖ Failed to connect to backend: %v", err))
		fmt.Println(color.YellowString("Make sure the backend is running: cd backend && mvn spring-boot:run"))
		return
	}
	defer resp.Body.Close()

	body, _ := io.ReadAll(resp.Body)

	if resp.StatusCode != http.StatusOK {
		fmt.Println(color.RedString("✖ Backend error: %s", string(body)))
		return
	}

	var result AgentRunResponse
	json.Unmarshal(body, &result)

	// Print iterations
	for _, msg := range result.Iterations {
		fmt.Print(msg)
	}

	// Print final result
	if result.Success {
		fmt.Println(color.GreenString("✅ All tests passed!"))
	} else {
		fmt.Println(color.YellowString("⚠️ " + result.Message))
		if result.ThreadID != "" {
			fmt.Println()
			fmt.Println(color.CyanString("Resume with: sdlc agent %s -t \"%s\" -f %s --thread %s",
				request.OutputFile, request.TestCommand, request.TestFile, result.ThreadID))
		}
	}
}

// deriveTestFile generates test file name from source file (micro-agent pattern)
func deriveTestFile(filePath string) string {
	ext := filepath.Ext(filePath)
	base := strings.TrimSuffix(filePath, ext)

	// Handle jsx/tsx -> js/ts for test files
	testExt := ext
	if ext == ".jsx" {
		testExt = ".js"
	} else if ext == ".tsx" {
		testExt = ".ts"
	}

	// Check for existing test/spec files
	testFile := base + ".test" + testExt
	if _, err := os.Stat(testFile); err == nil {
		return testFile
	}

	specFile := base + ".spec" + testExt
	if _, err := os.Stat(specFile); err == nil {
		return specFile
	}

	// Default to .test.ext
	return base + ".test" + testExt
}

// guessTestCommand guesses the test command based on file type
func guessTestCommand(filePath string) string {
	ext := filepath.Ext(filePath)

	switch ext {
	case ".js", ".jsx", ".ts", ".tsx":
		// Check for package.json
		if _, err := os.Stat("package.json"); err == nil {
			return "npm test"
		}
		return "npx jest"
	case ".py":
		return "pytest"
	case ".go":
		return "go test"
	case ".java":
		return "mvn test"
	case ".rs":
		return "cargo test"
	default:
		return "npm test"
	}
}

