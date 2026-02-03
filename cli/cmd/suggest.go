package cmd

import (
	"encoding/json"
	"fmt"
	"os"
	"strings"

	"github.com/spf13/cobra"
)

// SuggestRequest for command suggestion
type SuggestRequest struct {
	Input       string `json:"input"`
	ProjectPath string `json:"projectPath"`
}

// SuggestResponse from the backend
type SuggestResponse struct {
	Success     bool     `json:"success"`
	Suggestions []string `json:"suggestions"`
	Explanation string   `json:"explanation"`
	Error       string   `json:"error"`
}

var suggestCmd = &cobra.Command{
	Use:   "suggest [partial command or description]",
	Short: "Get AI-powered command suggestions",
	Long: `Get intelligent command suggestions based on partial input or description.

This command helps you:
- Correct typos in commands
- Find the right command syntax
- Discover available commands
- Get suggestions for what you might want to do

Examples:
  sdlc suggest "analize security"        # Suggests: "analyze security"
  sdlc suggest "how do I check tests"    # Suggests relevant test commands
  sdlc suggest "staus"                   # Suggests: "status"
  sdlc suggest "deploy to prod"          # Suggests release commands`,
	Args: cobra.MinimumNArgs(1),
	Run:  runSuggest,
}

func init() {
	rootCmd.AddCommand(suggestCmd)
}

func runSuggest(cmd *cobra.Command, args []string) {
	input := strings.Join(args, " ")
	
	// Get project path
	projectPath, _ := os.Getwd()
	
	// Get backend URL
	backendURL := os.Getenv("BACKEND_URL")
	if backendURL == "" {
		backendURL = "http://localhost:8080"
	}
	
	// Check if backend is available
	if !checkBackendAvailable(backendURL) {
		// Fall back to local suggestion if backend not available
		localSuggestions := getLocalSuggestions(input)
		displayLocalSuggestions(input, localSuggestions)
		return
	}
	
	// Query backend for suggestions
	response, err := fetchSuggestionsFromBackend(backendURL, input, projectPath)
	if err != nil {
		// Fall back to local suggestions
		localSuggestions := getLocalSuggestions(input)
		displayLocalSuggestions(input, localSuggestions)
		return
	}
	
	displaySuggestions(input, response)
}

func fetchSuggestionsFromBackend(backendURL, input, projectPath string) (*SuggestResponse, error) {
	request := SuggestRequest{
		Input:       input,
		ProjectPath: projectPath,
	}
	
	jsonData, err := json.Marshal(request)
	if err != nil {
		return nil, err
	}
	
	resp, err := makeHTTPRequest(backendURL+"/api/intent/suggest", "POST", jsonData)
	if err != nil {
		return nil, err
	}
	
	var response SuggestResponse
	if err := json.Unmarshal(resp, &response); err != nil {
		return nil, err
	}
	
	return &response, nil
}

func displaySuggestions(input string, response *SuggestResponse) {
	fmt.Println("═══════════════════════════════════════════════════════════════")
	fmt.Println("💡 Command Suggestions")
	fmt.Println("═══════════════════════════════════════════════════════════════")
	
	fmt.Printf("\nInput: %s\n", input)
	
	if !response.Success || len(response.Suggestions) == 0 {
		fmt.Println("\nNo suggestions available.")
		if response.Error != "" {
			fmt.Printf("Error: %s\n", response.Error)
		}
		return
	}
	
	if response.Explanation != "" {
		fmt.Printf("\n%s\n", response.Explanation)
	}
	
	fmt.Println("\nDid you mean:")
	for i, suggestion := range response.Suggestions {
		fmt.Printf("  %d. sdlc %s\n", i+1, suggestion)
	}
	
	fmt.Println("\n───────────────────────────────────────────────────────────────")
	fmt.Println("Run one of these commands or use 'sdlc ai \"your request\"' for")
	fmt.Println("AI-powered natural language processing.")
}

func displayLocalSuggestions(input string, suggestions []string) {
	fmt.Println("═══════════════════════════════════════════════════════════════")
	fmt.Println("💡 Command Suggestions (Offline)")
	fmt.Println("═══════════════════════════════════════════════════════════════")
	
	fmt.Printf("\nInput: %s\n", input)
	
	if len(suggestions) == 0 {
		fmt.Println("\nNo suggestions available.")
		showAvailableCommands()
		return
	}
	
	fmt.Println("\nDid you mean:")
	for i, suggestion := range suggestions {
		fmt.Printf("  %d. sdlc %s\n", i+1, suggestion)
	}
	
	fmt.Println("\n───────────────────────────────────────────────────────────────")
	fmt.Println("Start the backend for AI-powered suggestions:")
	fmt.Println("  cd backend && mvn spring-boot:run")
}

func getLocalSuggestions(input string) []string {
	input = strings.ToLower(input)
	suggestions := []string{}
	
	// Define command patterns and their corrections
	commandPatterns := map[string][]string{
		// Status variations
		"staus":   {"status"},
		"statsu":  {"status"},
		"sttaus":  {"status"},
		"check":   {"status"},
		"show":    {"status"},
		
		// Analyze variations
		"analize":   {"analyze security", "analyze performance"},
		"analyise":  {"analyze security", "analyze performance"},
		"analyz":    {"analyze security", "analyze performance"},
		"scan":      {"analyze security"},
		"inspect":   {"analyze"},
		"review":    {"analyze"},
		"audit":     {"analyze security"},
		
		// Improve variations
		"improv":    {"improve performance", "improve security"},
		"optimise":  {"improve performance"},
		"optimize":  {"improve performance"},
		"enhance":   {"improve"},
		"fix":       {"improve", "debug"},
		"boost":     {"improve performance"},
		
		// Test variations
		"tests":     {"test unit", "test coverage"},
		"testing":   {"test"},
		"run":       {"test unit", "exec"},
		"verify":    {"test"},
		"validate":  {"test"},
		
		// Debug variations
		"debugg":        {"debug"},
		"troubleshoot":  {"debug"},
		"diagnose":      {"debug"},
		"investigate":   {"debug"},
		
		// Release variations
		"deploy":    {"release staging", "release production"},
		"publish":   {"release"},
		"ship":      {"release production"},
		
		// Prepare variations
		"setup":     {"prepare release"},
		"configure": {"prepare"},
		"init":      {"prepare"},
		
		// Refactor variations
		"refacto":   {"refactor code"},
		"refactr":   {"refactor code"},
		"cleanup":   {"refactor code"},
		
		// AI command
		"ai":        {"ai \"your natural language request\""},
		"ask":       {"ai \"your question\""},
		"help":      {"--help", "ai \"what can I do?\""},
	}
	
	// Check for exact or partial matches
	for pattern, cmds := range commandPatterns {
		if strings.Contains(input, pattern) || strings.Contains(pattern, input) {
			suggestions = append(suggestions, cmds...)
		}
	}
	
	// Calculate Levenshtein distance for fuzzy matching
	if len(suggestions) == 0 {
		validCommands := []string{
			"status", "analyze", "improve", "test", "debug",
			"prepare", "release", "refactor", "ai", "exec", "suggest",
		}
		
		for _, cmd := range validCommands {
			if levenshteinDistance(input, cmd) <= 2 {
				suggestions = append(suggestions, cmd)
			}
		}
	}
	
	// Remove duplicates
	seen := make(map[string]bool)
	unique := []string{}
	for _, s := range suggestions {
		if !seen[s] {
			seen[s] = true
			unique = append(unique, s)
		}
	}
	
	return unique
}

func levenshteinDistance(s1, s2 string) int {
	s1 = strings.ToLower(s1)
	s2 = strings.ToLower(s2)
	
	if len(s1) == 0 {
		return len(s2)
	}
	if len(s2) == 0 {
		return len(s1)
	}
	
	matrix := make([][]int, len(s1)+1)
	for i := range matrix {
		matrix[i] = make([]int, len(s2)+1)
		matrix[i][0] = i
	}
	for j := range matrix[0] {
		matrix[0][j] = j
	}
	
	for i := 1; i <= len(s1); i++ {
		for j := 1; j <= len(s2); j++ {
			cost := 0
			if s1[i-1] != s2[j-1] {
				cost = 1
			}
			matrix[i][j] = minInt(
				matrix[i-1][j]+1,
				minInt(matrix[i][j-1]+1, matrix[i-1][j-1]+cost),
			)
		}
	}
	
	return matrix[len(s1)][len(s2)]
}

func minInt(a, b int) int {
	if a < b {
		return a
	}
	return b
}

func showAvailableCommands() {
	fmt.Println("\nAvailable commands:")
	fmt.Println("  sdlc status              - Show project status")
	fmt.Println("  sdlc analyze <target>    - Analyze security/performance")
	fmt.Println("  sdlc improve <target>    - Improve code quality")
	fmt.Println("  sdlc test <type>         - Run tests")
	fmt.Println("  sdlc debug               - Debug issues")
	fmt.Println("  sdlc prepare <action>    - Prepare for action")
	fmt.Println("  sdlc release <env>       - Release to environment")
	fmt.Println("  sdlc refactor <target>   - Refactor code")
	fmt.Println("  sdlc ai \"prompt\"         - AI-powered commands")
	fmt.Println("  sdlc exec \"command\"      - Execute SDLC command")
	fmt.Println("  sdlc suggest \"text\"      - Get command suggestions")
}

