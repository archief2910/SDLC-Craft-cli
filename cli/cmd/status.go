package cmd

import (
	"fmt"
	"os"

	"github.com/spf13/cobra"
	"github.com/sdlcraft/cli/client"
)

var statusCmd = &cobra.Command{
	Use:   "status [project-id]",
	Short: "Get the current SDLC state for a project",
	Long:  `Query the backend for the current SDLC state including phase, risk level, test coverage, and release readiness.`,
	Args:  cobra.MaximumNArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		projectID := "default"
		if len(args) > 0 {
			projectID = args[0]
		}

		// Create backend client
		backendURL := os.Getenv("BACKEND_URL")
		if backendURL == "" {
			backendURL = "http://localhost:8080"
		}

		client := client.NewHTTPBackendClient(backendURL, 0)

		// Check if backend is available
		if !client.IsAvailable() {
			fmt.Fprintf(os.Stderr, "Error: Backend is not available at %s\n", backendURL)
			fmt.Fprintf(os.Stderr, "Make sure the backend is running.\n")
			os.Exit(1)
		}

		// Query state
		state, err := client.QueryState(projectID)
		if err != nil {
			fmt.Fprintf(os.Stderr, "Error querying state: %v\n", err)
			os.Exit(1)
		}

		// Display state
		fmt.Printf("Project: %s\n", state.ProjectID)
		fmt.Printf("Phase: %s\n", state.CurrentPhase)
		fmt.Printf("Risk Level: %s\n", state.RiskLevel)
		fmt.Printf("Test Coverage: %.2f%%\n", state.TestCoverage*100)
		fmt.Printf("Open Issues: %d/%d\n", state.OpenIssues, state.TotalIssues)
		fmt.Printf("Release Readiness: %.2f%%\n", state.ReleaseReadiness*100)
		fmt.Printf("Updated: %s\n", state.UpdatedAt)
	},
}

func init() {
	rootCmd.AddCommand(statusCmd)
}
