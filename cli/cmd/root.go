package cmd

import (
	"fmt"
	"os"
	"strings"

	"github.com/spf13/cobra"
)

var rootCmd = &cobra.Command{
	Use:   "sdlc",
	Short: "SDLCraft CLI - Intent-aware SDLC orchestration tool",
	Long: `SDLCraft CLI is a production-grade, intent-aware, self-healing, agentic 
command-line tool for Software Development Life Cycle (SDLC) orchestration.

It acts as a compiler for developer intent - understanding what developers want 
to accomplish even when commands are imperfect, and executing complex SDLC 
workflows through autonomous agents.

Available Commands:
  ai        Execute natural language prompts using AI-powered RAG
  exec      Execute an SDLC command
  index     Index the codebase for RAG-based AI search
  status    Show SDLC state
  suggest   Get AI-powered command suggestions

Examples:
  sdlc ai "add error handling to the API"
  sdlc exec "analyze security"
  sdlc suggest "deploy to production"
  sdlc index`,
	// Handle unknown commands gracefully
	SilenceUsage:  true,
	SilenceErrors: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		if len(args) == 0 {
			return cmd.Help()
		}
		
		// Unknown command - suggest corrections
		fmt.Printf("❌ Unknown command: %s\n\n", strings.Join(args, " "))
		fmt.Println("💡 Did you mean one of these?")
		
		suggestions := getSuggestions(args[0])
		for _, s := range suggestions {
			fmt.Printf("   sdlc %s\n", s)
		}
		
		fmt.Println("\n📝 Or use natural language with the 'ai' command:")
		fmt.Printf("   sdlc ai \"%s\"\n", strings.Join(args, " "))
		fmt.Println("\nRun 'sdlc --help' for a list of available commands.")
		
		return nil
	},
}

func GetRootCmd() *cobra.Command {
	return rootCmd
}

func init() {
	// Add version flag
	rootCmd.Version = "0.1.0"
}

// getSuggestions returns command suggestions based on input
func getSuggestions(input string) []string {
	input = strings.ToLower(input)
	suggestions := []string{}
	
	// Command mappings
	commands := map[string][]string{
		"status":   {"status"},
		"analyze":  {"analyze security", "analyze performance"},
		"improve":  {"improve performance", "improve security"},
		"test":     {"test unit", "test coverage"},
		"debug":    {"debug"},
		"prepare":  {"prepare release"},
		"release":  {"release staging", "release production"},
		"refactor": {"refactor code"},
		"ai":       {"ai \"your prompt\""},
		"index":    {"index"},
		"suggest":  {"suggest \"your input\""},
		"exec":     {"exec \"your command\""},
	}
	
	// Check for fuzzy matches
	for cmd, sug := range commands {
		if strings.Contains(cmd, input) || strings.Contains(input, cmd) {
			suggestions = append(suggestions, sug...)
		}
	}
	
	// Check synonyms
	synonyms := map[string]string{
		"check": "status", "show": "status", "view": "status",
		"scan": "analyze", "audit": "analyze", "inspect": "analyze",
		"optimize": "improve", "enhance": "improve", "fix": "improve",
		"run": "test", "verify": "test",
		"deploy": "release", "publish": "release", "ship": "release",
		"setup": "prepare", "configure": "prepare",
		"ask": "ai", "help": "suggest",
	}
	
	for syn, cmd := range synonyms {
		if strings.Contains(input, syn) {
			if sug, ok := commands[cmd]; ok {
				suggestions = append(suggestions, sug...)
			}
		}
	}
	
	// Default suggestions
	if len(suggestions) == 0 {
		suggestions = []string{"status", "ai \"your prompt\"", "suggest \"" + input + "\""}
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
	
	// Limit to 5
	if len(unique) > 5 {
		unique = unique[:5]
	}
	
	return unique
}

// Execute runs the root command
func Execute() {
	if err := rootCmd.Execute(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
