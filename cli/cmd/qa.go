package cmd

import (
	"encoding/json"
	"fmt"
	"os"

	"github.com/sdlcraft/cli/client"
	"github.com/spf13/cobra"
)

var qaCmd = &cobra.Command{
	Use:   "qa",
	Short: "QA and testing automation",
	Long: `QA automation commands for running tests, generating tests with AI,
and executing iterative test-driven development loops.

Commands:
  run       Run test suite
  generate  Generate tests from code using AI
  fix       Iterative test-driven fixing (micro-agent pattern)
  coverage  Analyze test coverage`,
}

var qaRunCmd = &cobra.Command{
	Use:   "run",
	Short: "Run test suite",
	RunE: func(cmd *cobra.Command, args []string) error {
		workingDir, _ := cmd.Flags().GetString("dir")
		testCommand, _ := cmd.Flags().GetString("command")

		if workingDir == "" {
			workingDir, _ = os.Getwd()
		}

		c := client.New()
		body, _ := json.Marshal(map[string]string{
			"action":      "runTests",
			"workingDir":  workingDir,
			"testCommand": testCommand,
		})

		fmt.Println("🧪 Running tests...")
		resp, err := c.Post("/api/integration/qa/execute", body)
		if err != nil {
			return fmt.Errorf("failed to run tests: %w", err)
		}

		var result map[string]interface{}
		json.Unmarshal(resp, &result)

		if result["success"].(bool) {
			data := result["data"].(map[string]interface{})
			fmt.Printf("✅ Tests passed!\n")
			fmt.Printf("   Passed: %v\n", data["passed"])
			fmt.Printf("   Failed: %v\n", data["failed"])
			fmt.Printf("   Skipped: %v\n", data["skipped"])
		} else {
			data := result["data"].(map[string]interface{})
			fmt.Printf("❌ Tests failed\n")
			fmt.Printf("   Passed: %v\n", data["passed"])
			fmt.Printf("   Failed: %v\n", data["failed"])
			if output, ok := data["output"].(string); ok && len(output) > 0 {
				fmt.Printf("\n📋 Output:\n%s\n", truncateString(output, 2000))
			}
		}

		return nil
	},
}

var qaGenerateCmd = &cobra.Command{
	Use:   "generate [source-file]",
	Short: "Generate tests from code using AI",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		sourceFile := args[0]
		testFile, _ := cmd.Flags().GetString("output")

		if testFile == "" {
			// Auto-generate test file name
			testFile = generateTestFileName(sourceFile)
		}

		c := client.New()
		body, _ := json.Marshal(map[string]string{
			"action":   "generateTests",
			"codeFile": sourceFile,
			"testFile": testFile,
		})

		fmt.Printf("🤖 Generating tests for %s...\n", sourceFile)
		resp, err := c.Post("/api/integration/qa/execute", body)
		if err != nil {
			return fmt.Errorf("failed to generate tests: %w", err)
		}

		var result map[string]interface{}
		json.Unmarshal(resp, &result)

		if result["success"].(bool) {
			data := result["data"].(map[string]interface{})
			fmt.Printf("✅ Tests generated: %s\n", data["testFile"])
			fmt.Printf("   Lines: %v\n", data["linesGenerated"])
		} else {
			fmt.Printf("❌ %s\n", result["message"])
		}

		return nil
	},
}

var qaFixCmd = &cobra.Command{
	Use:   "fix [source-file]",
	Short: "Iterative test-driven fixing (micro-agent pattern)",
	Long: `Runs an iterative loop that:
1. Runs tests
2. If tests fail, uses AI to fix the code
3. Repeats until tests pass or max iterations reached

This follows the micro-agent pattern for autonomous code fixing.`,
	Args: cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		sourceFile := args[0]
		testFile, _ := cmd.Flags().GetString("test")
		workingDir, _ := cmd.Flags().GetString("dir")
		maxIterations, _ := cmd.Flags().GetInt("max-iterations")

		if testFile == "" {
			testFile = generateTestFileName(sourceFile)
		}
		if workingDir == "" {
			workingDir, _ = os.Getwd()
		}

		c := client.New()
		body, _ := json.Marshal(map[string]interface{}{
			"action":        "iterativeTestFix",
			"codeFile":      sourceFile,
			"testFile":      testFile,
			"workingDir":    workingDir,
			"maxIterations": maxIterations,
		})

		fmt.Printf("🔄 Starting iterative fix loop for %s\n", sourceFile)
		fmt.Printf("   Test file: %s\n", testFile)
		fmt.Printf("   Max iterations: %d\n\n", maxIterations)

		resp, err := c.Post("/api/integration/qa/execute", body)
		if err != nil {
			return fmt.Errorf("failed to run iterative fix: %w", err)
		}

		var result map[string]interface{}
		json.Unmarshal(resp, &result)

		if result["success"].(bool) {
			data := result["data"].(map[string]interface{})
			fmt.Printf("✅ All tests pass after %v iterations!\n", data["iterations"])
		} else {
			data := result["data"].(map[string]interface{})
			fmt.Printf("❌ Max iterations (%v) reached\n", data["iterations"])
			if lastError, ok := data["lastError"].(string); ok {
				fmt.Printf("\n📋 Last error:\n%s\n", truncateString(lastError, 1500))
			}
		}

		return nil
	},
}

var qaCoverageCmd = &cobra.Command{
	Use:   "coverage",
	Short: "Analyze test coverage",
	RunE: func(cmd *cobra.Command, args []string) error {
		workingDir, _ := cmd.Flags().GetString("dir")

		if workingDir == "" {
			workingDir, _ = os.Getwd()
		}

		c := client.New()
		body, _ := json.Marshal(map[string]string{
			"action":     "analyzeCoverage",
			"workingDir": workingDir,
		})

		fmt.Println("📊 Analyzing coverage...")
		resp, err := c.Post("/api/integration/qa/execute", body)
		if err != nil {
			return fmt.Errorf("failed to analyze coverage: %w", err)
		}

		var result map[string]interface{}
		json.Unmarshal(resp, &result)

		data := result["data"].(map[string]interface{})
		
		meetsThreshold, _ := data["meetsThreshold"].(bool)
		status := "✅"
		if !meetsThreshold {
			status = "⚠️"
		}

		fmt.Printf("%s Coverage Analysis:\n", status)
		fmt.Printf("   Line Coverage:     %.1f%%\n", data["lineCoverage"])
		fmt.Printf("   Branch Coverage:   %.1f%%\n", data["branchCoverage"])
		fmt.Printf("   Function Coverage: %.1f%%\n", data["functionCoverage"])
		fmt.Printf("   Threshold:         %v%%\n", data["threshold"])

		if meetsThreshold {
			fmt.Println("\n✅ Coverage meets threshold!")
		} else {
			fmt.Println("\n⚠️  Coverage below threshold")
		}

		return nil
	},
}

func generateTestFileName(sourceFile string) string {
	// Simple logic to generate test file name
	ext := ""
	base := sourceFile
	
	for _, e := range []string{".ts", ".tsx", ".js", ".jsx", ".py", ".go", ".java"} {
		if len(sourceFile) > len(e) && sourceFile[len(sourceFile)-len(e):] == e {
			ext = e
			base = sourceFile[:len(sourceFile)-len(e)]
			break
		}
	}

	switch ext {
	case ".ts", ".tsx":
		return base + ".test.ts"
	case ".js", ".jsx":
		return base + ".test.js"
	case ".py":
		return base + "_test.py"
	case ".go":
		return base + "_test.go"
	case ".java":
		return base + "Test.java"
	default:
		return sourceFile + ".test"
	}
}

func truncateString(s string, maxLen int) string {
	if len(s) <= maxLen {
		return s
	}
	return s[:maxLen] + "... (truncated)"
}

func init() {
	rootCmd.AddCommand(qaCmd)
	qaCmd.AddCommand(qaRunCmd)
	qaCmd.AddCommand(qaGenerateCmd)
	qaCmd.AddCommand(qaFixCmd)
	qaCmd.AddCommand(qaCoverageCmd)

	// Common flags
	qaRunCmd.Flags().StringP("dir", "d", "", "Working directory")
	qaRunCmd.Flags().StringP("command", "c", "", "Test command (default: auto-detect)")

	qaGenerateCmd.Flags().StringP("output", "o", "", "Output test file path")

	qaFixCmd.Flags().StringP("test", "t", "", "Test file path")
	qaFixCmd.Flags().StringP("dir", "d", "", "Working directory")
	qaFixCmd.Flags().IntP("max-iterations", "m", 20, "Maximum fix iterations")

	qaCoverageCmd.Flags().StringP("dir", "d", "", "Working directory")
}

