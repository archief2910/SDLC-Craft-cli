package cmd

import (
	"fmt"
	"os"
	"strings"

	"github.com/spf13/cobra"
	"github.com/sdlcraft/cli/client"
)

var executeCmd = &cobra.Command{
	Use:   "exec [command]",
	Short: "Execute a natural language SDLC command",
	Long:  `Execute any SDLC command in natural language. The system will infer your intent and execute it using AI agents.`,
	Args:  cobra.MinimumNArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		rawCommand := strings.Join(args, " ")
		
		// Create backend client
		backendURL := os.Getenv("BACKEND_URL")
		if backendURL == "" {
			backendURL = "http://localhost:8080"
		}

		c := client.NewHTTPBackendClient(backendURL, 0)

		// Check if backend is available
		if !c.IsAvailable() {
			fmt.Fprintf(os.Stderr, "Error: Backend is not available at %s\n", backendURL)
			os.Exit(1)
		}

		// Infer intent
		fmt.Printf("Processing: %s\n\n", rawCommand)
		
		request := &client.IntentRequest{
			RawCommand:  rawCommand,
			UserID:      "default-user",
			ProjectID:   "default",
			ProjectPath: ".",
			Context:     make(map[string]interface{}),
		}

		response, err := c.InferIntent(request)
		if err != nil {
			fmt.Fprintf(os.Stderr, "Error inferring intent: %v\n", err)
			os.Exit(1)
		}

		// Display inferred intent
		fmt.Printf("Intent: %s\n", response.Intent)
		fmt.Printf("Target: %s\n", response.Target)
		fmt.Printf("Confidence: %.2f%%\n", response.Confidence*100)
		fmt.Printf("Explanation: %s\n\n", response.Explanation)

		// Check if confirmation is required
		if response.RequiresConfirmation {
			fmt.Printf("⚠️  Risk Level: %s\n", response.RiskLevel)
			fmt.Printf("Impact: %s\n", response.ImpactDescription)
			fmt.Print("\nDo you want to proceed? (yes/no): ")
			
			var confirmation string
			fmt.Scanln(&confirmation)
			
			if strings.ToLower(confirmation) != "yes" && strings.ToLower(confirmation) != "y" {
				fmt.Println("Operation cancelled.")
				os.Exit(0)
			}
		}

		// Execute the intent
		fmt.Println("\nExecuting...")
		events, err := c.ExecuteIntent(request)
		if err != nil {
			fmt.Fprintf(os.Stderr, "Error executing intent: %v\n", err)
			os.Exit(1)
		}

		// Stream execution events
		for event := range events {
			switch event.Type {
			case "status":
				fmt.Printf("⏳ %s\n", event.Message)
			case "progress":
				fmt.Printf("📊 Progress: %d%%\n", event.Progress)
			case "completion":
				fmt.Printf("✅ %s\n", event.Message)
			case "error":
				fmt.Printf("❌ %s\n", event.Message)
			default:
				fmt.Printf("ℹ️  %s\n", event.Message)
			}
		}
	},
}

func init() {
	rootCmd.AddCommand(executeCmd)
}
