package cmd

import (
	"encoding/json"
	"fmt"
	"os"

	"github.com/sdlcraft/cli/client"
	"github.com/spf13/cobra"
)

var workflowCmd = &cobra.Command{
	Use:   "workflow",
	Short: "Execute SDLC workflows",
	Long: `Execute predefined or custom SDLC workflows.

Workflows combine multiple integrations (Jira, Bitbucket, AWS, Docker, QA)
into automated pipelines following the micro-agent iterative pattern.

Available Workflows:
  bug-fix     Complete bug fix from Jira ticket to deployment
  feature-tdd Test-driven feature development with AI
  release     Full release pipeline with Docker and AWS
  ci          Continuous integration pipeline`,
}

var workflowListCmd = &cobra.Command{
	Use:   "list",
	Short: "List available workflows",
	RunE: func(cmd *cobra.Command, args []string) error {
		c := client.New()
		resp, err := c.Get("/api/workflow")
		if err != nil {
			return fmt.Errorf("failed to list workflows: %w", err)
		}

		var workflows []map[string]interface{}
		if err := json.Unmarshal(resp, &workflows); err != nil {
			return fmt.Errorf("failed to parse response: %w", err)
		}

		fmt.Println("📋 Available Workflows:")
		fmt.Println()
		for _, w := range workflows {
			fmt.Printf("  %s\n", w["id"])
			fmt.Printf("    Name: %s\n", w["name"])
			fmt.Printf("    Description: %s\n", w["description"])
			fmt.Printf("    Steps: %v\n", w["stepCount"])
			fmt.Println()
		}
		return nil
	},
}

var workflowRunCmd = &cobra.Command{
	Use:   "run [workflow-id]",
	Short: "Execute a workflow",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		workflowID := args[0]
		async, _ := cmd.Flags().GetBool("async")

		// Build context from flags
		context := make(map[string]interface{})
		
		// Add common context variables
		if v, _ := cmd.Flags().GetString("jira-ticket"); v != "" {
			context["jiraTicket"] = v
		}
		if v, _ := cmd.Flags().GetString("working-dir"); v != "" {
			context["workingDir"] = v
		} else {
			wd, _ := os.Getwd()
			context["workingDir"] = wd
		}
		if v, _ := cmd.Flags().GetString("repo"); v != "" {
			context["repo"] = v
		}
		if v, _ := cmd.Flags().GetString("source-file"); v != "" {
			context["sourceFile"] = v
		}
		if v, _ := cmd.Flags().GetString("test-file"); v != "" {
			context["testFile"] = v
		}
		if v, _ := cmd.Flags().GetString("version"); v != "" {
			context["version"] = v
		}
		if v, _ := cmd.Flags().GetString("registry"); v != "" {
			context["registry"] = v
		}
		if v, _ := cmd.Flags().GetString("image-name"); v != "" {
			context["imageName"] = v
		}
		if v, _ := cmd.Flags().GetString("cluster"); v != "" {
			context["cluster"] = v
		}
		if v, _ := cmd.Flags().GetString("service"); v != "" {
			context["service"] = v
		}

		body, _ := json.Marshal(context)
		c := client.New()

		var endpoint string
		if async {
			endpoint = fmt.Sprintf("/api/workflow/%s/execute-async", workflowID)
		} else {
			endpoint = fmt.Sprintf("/api/workflow/%s/execute", workflowID)
		}

		fmt.Printf("🚀 Starting workflow: %s\n", workflowID)
		resp, err := c.Post(endpoint, body)
		if err != nil {
			return fmt.Errorf("failed to execute workflow: %w", err)
		}

		var result map[string]interface{}
		if err := json.Unmarshal(resp, &result); err != nil {
			return fmt.Errorf("failed to parse response: %w", err)
		}

		if async {
			fmt.Printf("✅ Workflow started asynchronously\n")
			fmt.Printf("   Execution ID: %s\n", result["executionId"])
			fmt.Printf("   Check status with: sdlc workflow status %s\n", result["executionId"])
		} else {
			success, _ := result["success"].(bool)
			if success {
				fmt.Printf("✅ Workflow completed successfully!\n")
			} else {
				fmt.Printf("❌ Workflow failed\n")
			}
			
			if summary, ok := result["summary"].(string); ok {
				fmt.Printf("   %s\n", summary)
			}

			// Print step results
			if steps, ok := result["stepResults"].([]interface{}); ok {
				fmt.Println("\n📊 Step Results:")
				for _, s := range steps {
					step := s.(map[string]interface{})
					status := "✅"
					if !step["success"].(bool) {
						status = "❌"
					}
					fmt.Printf("   %s %s: %s\n", status, step["stepName"], step["message"])
				}
			}
		}

		return nil
	},
}

var workflowStatusCmd = &cobra.Command{
	Use:   "status [execution-id]",
	Short: "Check workflow execution status",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		executionID := args[0]
		c := client.New()

		resp, err := c.Get(fmt.Sprintf("/api/workflow/execution/%s", executionID))
		if err != nil {
			return fmt.Errorf("failed to get execution status: %w", err)
		}

		var status map[string]interface{}
		if err := json.Unmarshal(resp, &status); err != nil {
			return fmt.Errorf("failed to parse response: %w", err)
		}

		fmt.Printf("📊 Execution Status: %s\n", status["status"])
		
		if status["status"] == "COMPLETED" {
			if status["success"].(bool) {
				fmt.Println("✅ Workflow completed successfully!")
			} else {
				fmt.Println("❌ Workflow failed")
			}
			if summary, ok := status["summary"].(string); ok {
				fmt.Printf("   %s\n", summary)
			}
		} else if status["status"] == "FAILED" {
			fmt.Printf("❌ Error: %s\n", status["error"])
		} else {
			fmt.Println("⏳ Workflow still running...")
		}

		return nil
	},
}

func init() {
	rootCmd.AddCommand(workflowCmd)
	workflowCmd.AddCommand(workflowListCmd)
	workflowCmd.AddCommand(workflowRunCmd)
	workflowCmd.AddCommand(workflowStatusCmd)

	// Run command flags
	workflowRunCmd.Flags().Bool("async", false, "Run workflow asynchronously")
	workflowRunCmd.Flags().String("jira-ticket", "", "Jira ticket ID (e.g., PROJ-123)")
	workflowRunCmd.Flags().String("working-dir", "", "Working directory (default: current)")
	workflowRunCmd.Flags().String("repo", "", "Repository slug")
	workflowRunCmd.Flags().String("source-file", "", "Source file path")
	workflowRunCmd.Flags().String("test-file", "", "Test file path")
	workflowRunCmd.Flags().String("version", "", "Version tag")
	workflowRunCmd.Flags().String("registry", "", "Docker registry")
	workflowRunCmd.Flags().String("image-name", "", "Docker image name")
	workflowRunCmd.Flags().String("cluster", "", "AWS ECS cluster")
	workflowRunCmd.Flags().String("service", "", "AWS ECS service")
}

