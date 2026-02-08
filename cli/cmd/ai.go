package cmd

import (
	"bufio"
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/spf13/cobra"
)

// RAG query request
type RAGQueryRequest struct {
	Query       string `json:"query"`
	ProjectPath string `json:"projectPath"`
	FocusFile   string `json:"focusFile,omitempty"`
}

// RAG query response
type RAGQueryResponse struct {
	Success         bool                     `json:"success"`
	Query           string                   `json:"query"`
	Explanation     string                   `json:"explanation"`
	ContextChunks   int                      `json:"contextChunks"`
	ReferencedFiles []string                 `json:"referencedFiles"`
	Changes         []map[string]interface{} `json:"changes"`
	Commands        []string                 `json:"commands"`
	Error           string                   `json:"error"`
}

// Apply changes request
type ApplyChangesRequest struct {
	Changes     []map[string]interface{} `json:"changes"`
	ProjectPath string                   `json:"projectPath"`
	DryRun      bool                     `json:"dryRun"`
}

var aiCmd = &cobra.Command{
	Use:   "ai [prompt]",
	Short: "Execute natural language prompts using AI-powered RAG",
	Long: `Execute any natural language prompt against your codebase using AI-powered RAG.

The AI will:
1. Search your codebase for relevant context
2. Understand your request using LLM
3. Suggest code changes based on context
4. Optionally apply changes automatically

Examples:
  sdlc ai "add error handling to the main function"
  sdlc ai "refactor the authentication module for better security"
  sdlc ai "explain how the payment processing works"
  sdlc ai --apply "add input validation to all API endpoints"
  sdlc ai --file src/main.go "optimize this file for performance"`,
	Args: cobra.MinimumNArgs(1),
	Run:  runAI,
}

var (
	applyChanges bool
	focusFile    string
	dryRun       bool
	interactive  bool
	projectDir   string
)

func init() {
	rootCmd.AddCommand(aiCmd)
	aiCmd.Flags().BoolVarP(&applyChanges, "apply", "a", false, "Apply suggested changes automatically")
	aiCmd.Flags().StringVarP(&focusFile, "file", "f", "", "Focus on a specific file")
	aiCmd.Flags().BoolVarP(&dryRun, "dry-run", "d", false, "Show what changes would be made without applying")
	aiCmd.Flags().BoolVarP(&interactive, "interactive", "i", false, "Interactive mode - confirm each change")
	aiCmd.Flags().StringVarP(&projectDir, "project", "p", "", "Path to project to analyze (default: current directory)")
}

func runAI(cmd *cobra.Command, args []string) {
	prompt := strings.Join(args, " ")
	
	// Get project path
	var projectPath string
	var err error
	
	if projectDir != "" {
		// Use specified project directory
		projectPath, err = filepath.Abs(projectDir)
		if err != nil {
			fmt.Fprintf(os.Stderr, "Error resolving project path: %v\n", err)
			os.Exit(1)
		}
	} else {
		// Use current directory
		projectPath, err = os.Getwd()
		if err != nil {
			fmt.Fprintf(os.Stderr, "Error getting current directory: %v\n", err)
			os.Exit(1)
		}
	}
	
	// Create backend client
	backendURL := os.Getenv("BACKEND_URL")
	if backendURL == "" {
		backendURL = "http://localhost:8080"
	}
	
	// Check if backend is available
	if !checkBackendAvailable(backendURL) {
		fmt.Fprintf(os.Stderr, "❌ Backend is not available at %s\n", backendURL)
		fmt.Fprintf(os.Stderr, "   Please start the backend with: cd backend && mvn spring-boot:run\n")
		os.Exit(1)
	}
	
	// Index the project first
	fmt.Printf("📁 Project: %s\n", projectPath)
	fmt.Println("🔍 Indexing codebase...")
	if err := indexProject(backendURL, projectPath); err != nil {
		fmt.Fprintf(os.Stderr, "⚠️ Warning: Could not index project: %v\n", err)
	}
	
	// Show processing message
	fmt.Println("🤖 Processing your request...")
	fmt.Printf("   Prompt: %s\n\n", prompt)
	
	// Make RAG query
	response, err := queryRAG(backendURL, prompt, projectPath, focusFile)
	if err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error querying AI: %v\n", err)
		os.Exit(1)
	}
	
	if !response.Success {
		fmt.Fprintf(os.Stderr, "❌ AI query failed: %s\n", response.Error)
		os.Exit(1)
	}
	
	// Display results
	displayResults(response)
	
	// Handle changes
	if len(response.Changes) > 0 {
		if applyChanges || dryRun {
			applyCodeChanges(backendURL, response.Changes, projectPath, dryRun)
		} else if interactive {
			interactiveApply(backendURL, response.Changes, projectPath)
		} else {
			fmt.Println("\n💡 To apply these changes, run with --apply or -a flag")
			fmt.Println("   To preview changes without applying, use --dry-run or -d")
			fmt.Println("   For interactive mode, use --interactive or -i")
		}
	}
	
	// Show suggested commands
	if len(response.Commands) > 0 {
		fmt.Println("\n📋 Suggested commands to run:")
		for _, c := range response.Commands {
			fmt.Printf("   $ %s\n", c)
		}
	}
}

func checkBackendAvailable(backendURL string) bool {
	client := &http.Client{Timeout: 5 * time.Second}
	resp, err := client.Get(backendURL + "/actuator/health")
	if err != nil {
		return false
	}
	defer resp.Body.Close()
	return resp.StatusCode == http.StatusOK
}

func queryRAG(backendURL, prompt, projectPath, focusFile string) (*RAGQueryResponse, error) {
	request := RAGQueryRequest{
		Query:       prompt,
		ProjectPath: projectPath,
		FocusFile:   focusFile,
	}
	
	jsonData, err := json.Marshal(request)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal request: %w", err)
	}
	
	// Make HTTP request to RAG endpoint
	resp, err := makeHTTPRequest(backendURL+"/api/rag/query", "POST", jsonData)
	if err != nil {
		return nil, err
	}
	
	var response RAGQueryResponse
	if err := json.Unmarshal(resp, &response); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}
	
	return &response, nil
}

func displayResults(response *RAGQueryResponse) {
	fmt.Println("═══════════════════════════════════════════════════════════════")
	fmt.Println("📊 AI Analysis Results")
	fmt.Println("═══════════════════════════════════════════════════════════════")
	
	// Explanation
	fmt.Println("\n📝 Explanation:")
	fmt.Println(wrapText(response.Explanation, 70))
	
	// Context
	fmt.Printf("\n📚 Analyzed %d code chunks from:\n", response.ContextChunks)
	for _, file := range response.ReferencedFiles {
		fmt.Printf("   • %s\n", filepath.Base(file))
	}
	
	// Changes
	if len(response.Changes) > 0 {
		fmt.Printf("\n🔧 Suggested Changes (%d):\n", len(response.Changes))
		fmt.Println("───────────────────────────────────────────────────────────────")
		
		for i, change := range response.Changes {
			file, _ := change["file"].(string)
			action, _ := change["action"].(string)
			desc, _ := change["description"].(string)
			
			actionIcon := getActionIcon(action)
			fmt.Printf("\n%d. %s [%s] %s\n", i+1, actionIcon, action, file)
			if desc != "" {
				fmt.Printf("   %s\n", desc)
			}
			
			// Show diff if available
			if diff, ok := change["diff"].(string); ok && diff != "" {
				fmt.Println("   ┌─────────────────────────────────────")
				for _, line := range strings.Split(diff, "\n") {
					prefix := "   │ "
					if strings.HasPrefix(line, "-") {
						fmt.Printf("%s\033[31m%s\033[0m\n", prefix, line)
					} else if strings.HasPrefix(line, "+") {
						fmt.Printf("%s\033[32m%s\033[0m\n", prefix, line)
					} else {
						fmt.Printf("%s%s\n", prefix, line)
					}
				}
				fmt.Println("   └─────────────────────────────────────")
			}
		}
	} else {
		fmt.Println("\n✓ No code changes needed")
	}
}

func getActionIcon(action string) string {
	switch strings.ToUpper(action) {
	case "CREATE":
		return "➕"
	case "MODIFY":
		return "✏️"
	case "DELETE":
		return "🗑️"
	default:
		return "📄"
	}
}

func applyCodeChanges(backendURL string, changes []map[string]interface{}, projectPath string, dryRun bool) {
	fmt.Println("\n═══════════════════════════════════════════════════════════════")
	if dryRun {
		fmt.Println("🔍 Dry Run - Changes that would be applied:")
	} else {
		fmt.Println("⚡ Applying Changes...")
	}
	fmt.Println("═══════════════════════════════════════════════════════════════")
	
	request := ApplyChangesRequest{
		Changes:     changes,
		ProjectPath: projectPath,
		DryRun:      dryRun,
	}
	
	jsonData, err := json.Marshal(request)
	if err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error: %v\n", err)
		return
	}
	
	resp, err := makeHTTPRequest(backendURL+"/api/rag/apply", "POST", jsonData)
	if err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error applying changes: %v\n", err)
		return
	}
	
	var result map[string]interface{}
	if err := json.Unmarshal(resp, &result); err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error parsing response: %v\n", err)
		return
	}
	
	// Display results
	if success, ok := result["success"].(bool); ok && success {
		if dryRun {
			fmt.Println("\n✓ Dry run completed successfully")
		} else {
			fmt.Println("\n✅ Changes applied successfully!")
		}
	} else {
		fmt.Println("\n⚠️ Some changes could not be applied")
	}
	
	if summary, ok := result["summary"].(string); ok {
		fmt.Printf("   %s\n", summary)
	}
	
	if results, ok := result["results"].([]interface{}); ok {
		for _, r := range results {
			if resMap, ok := r.(map[string]interface{}); ok {
				file, _ := resMap["file"].(string)
				action, _ := resMap["action"].(string)
				success, _ := resMap["success"].(bool)
				message, _ := resMap["message"].(string)
				
				icon := "✓"
				if !success {
					icon = "✗"
				}
				fmt.Printf("   %s [%s] %s: %s\n", icon, action, file, message)
			}
		}
	}
}

func interactiveApply(backendURL string, changes []map[string]interface{}, projectPath string) {
	reader := bufio.NewReader(os.Stdin)
	
	fmt.Println("\n═══════════════════════════════════════════════════════════════")
	fmt.Println("🔄 Interactive Mode - Review each change")
	fmt.Println("═══════════════════════════════════════════════════════════════")
	
	var selectedChanges []map[string]interface{}
	
	for i, change := range changes {
		file, _ := change["file"].(string)
		action, _ := change["action"].(string)
		desc, _ := change["description"].(string)
		
		fmt.Printf("\n[%d/%d] %s %s\n", i+1, len(changes), getActionIcon(action), file)
		fmt.Printf("       %s\n", desc)
		
		if diff, ok := change["diff"].(string); ok && diff != "" {
			fmt.Println("\nDiff preview:")
			for _, line := range strings.Split(diff, "\n") {
				if strings.HasPrefix(line, "-") {
					fmt.Printf("  \033[31m%s\033[0m\n", line)
				} else if strings.HasPrefix(line, "+") {
					fmt.Printf("  \033[32m%s\033[0m\n", line)
				} else {
					fmt.Printf("  %s\n", line)
				}
			}
		}
		
		fmt.Print("\nApply this change? [y/n/q]: ")
		input, _ := reader.ReadString('\n')
		input = strings.TrimSpace(strings.ToLower(input))
		
		switch input {
		case "y", "yes":
			selectedChanges = append(selectedChanges, change)
			fmt.Println("✓ Change selected")
		case "q", "quit":
			fmt.Println("Exiting interactive mode")
			break
		default:
			fmt.Println("✗ Change skipped")
		}
	}
	
	if len(selectedChanges) > 0 {
		fmt.Printf("\n%d changes selected. Apply them? [y/n]: ", len(selectedChanges))
		input, _ := reader.ReadString('\n')
		input = strings.TrimSpace(strings.ToLower(input))
		
		if input == "y" || input == "yes" {
			applyCodeChanges(backendURL, selectedChanges, projectPath, false)
		} else {
			fmt.Println("Changes not applied")
		}
	} else {
		fmt.Println("\nNo changes selected")
	}
}

func wrapText(text string, width int) string {
	words := strings.Fields(text)
	if len(words) == 0 {
		return ""
	}
	
	var lines []string
	var currentLine strings.Builder
	
	for _, word := range words {
		if currentLine.Len()+len(word)+1 > width {
			lines = append(lines, currentLine.String())
			currentLine.Reset()
		}
		if currentLine.Len() > 0 {
			currentLine.WriteString(" ")
		}
		currentLine.WriteString(word)
	}
	
	if currentLine.Len() > 0 {
		lines = append(lines, currentLine.String())
	}
	
	result := ""
	for _, line := range lines {
		result += "   " + line + "\n"
	}
	return strings.TrimSuffix(result, "\n")
}

func indexProject(backendURL, projectPath string) error {
	request := map[string]string{
		"projectPath": projectPath,
	}
	
	jsonData, err := json.Marshal(request)
	if err != nil {
		return err
	}
	
	resp, err := makeHTTPRequest(backendURL+"/api/rag/index", "POST", jsonData)
	if err != nil {
		return err
	}
	
	var result map[string]interface{}
	if err := json.Unmarshal(resp, &result); err != nil {
		return err
	}
	
	if success, ok := result["success"].(bool); !ok || !success {
		if errMsg, ok := result["error"].(string); ok {
			return fmt.Errorf("%s", errMsg)
		}
		return fmt.Errorf("indexing failed")
	}
	
	fmt.Println("✅ Codebase indexed!")
	return nil
}

func makeHTTPRequest(url, method string, body []byte) ([]byte, error) {
	client := &http.Client{Timeout: 300 * time.Second} // 5 minutes for Ollama
	
	req, err := http.NewRequest(method, url, bytes.NewBuffer(body))
	if err != nil {
		return nil, err
	}
	
	req.Header.Set("Content-Type", "application/json")
	
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	
	return io.ReadAll(resp.Body)
}
