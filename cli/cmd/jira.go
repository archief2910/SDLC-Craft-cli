package cmd

import (
	"encoding/json"
	"fmt"

	"github.com/sdlcraft/cli/client"
	"github.com/spf13/cobra"
)

var jiraCmd = &cobra.Command{
	Use:   "jira",
	Short: "Jira integration commands",
	Long: `Manage Jira tickets and workflows.

Commands:
  get      Get issue details
  create   Create a new issue
  transition Move issue to new status
  comment  Add a comment to an issue
  search   Search issues with JQL`,
}

var jiraGetCmd = &cobra.Command{
	Use:   "get [issue-key]",
	Short: "Get Jira issue details",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		issueKey := args[0]
		c := client.New()

		body, _ := json.Marshal(map[string]string{
			"action":   "getIssue",
			"issueKey": issueKey,
		})

		resp, err := c.Post("/api/integration/jira/execute", body)
		if err != nil {
			return fmt.Errorf("failed to get issue: %w", err)
		}

		var result map[string]interface{}
		json.Unmarshal(resp, &result)

		if result["success"].(bool) {
			data := result["data"].(map[string]interface{})
			fields := data["fields"].(map[string]interface{})
			
			fmt.Printf("🎫 %s: %s\n", issueKey, fields["summary"])
			fmt.Printf("   Status: %v\n", getNestedString(fields, "status", "name"))
			fmt.Printf("   Type: %v\n", getNestedString(fields, "issuetype", "name"))
			fmt.Printf("   Priority: %v\n", getNestedString(fields, "priority", "name"))
			if assignee := getNestedString(fields, "assignee", "displayName"); assignee != "" {
				fmt.Printf("   Assignee: %s\n", assignee)
			}
		} else {
			fmt.Printf("❌ %s\n", result["message"])
		}

		return nil
	},
}

var jiraCreateCmd = &cobra.Command{
	Use:   "create",
	Short: "Create a new Jira issue",
	RunE: func(cmd *cobra.Command, args []string) error {
		project, _ := cmd.Flags().GetString("project")
		issueType, _ := cmd.Flags().GetString("type")
		summary, _ := cmd.Flags().GetString("summary")
		description, _ := cmd.Flags().GetString("description")

		c := client.New()
		body, _ := json.Marshal(map[string]string{
			"action":      "createIssue",
			"projectKey":  project,
			"issueType":   issueType,
			"summary":     summary,
			"description": description,
		})

		resp, err := c.Post("/api/integration/jira/execute", body)
		if err != nil {
			return fmt.Errorf("failed to create issue: %w", err)
		}

		var result map[string]interface{}
		json.Unmarshal(resp, &result)

		if result["success"].(bool) {
			data := result["data"].(map[string]interface{})
			fmt.Printf("✅ Created issue: %s\n", data["key"])
		} else {
			fmt.Printf("❌ %s\n", result["message"])
		}

		return nil
	},
}

var jiraTransitionCmd = &cobra.Command{
	Use:   "transition [issue-key] [status]",
	Short: "Transition issue to new status",
	Args:  cobra.ExactArgs(2),
	RunE: func(cmd *cobra.Command, args []string) error {
		issueKey := args[0]
		status := args[1]

		c := client.New()
		body, _ := json.Marshal(map[string]string{
			"action":         "transitionIssue",
			"issueKey":       issueKey,
			"transitionName": status,
		})

		resp, err := c.Post("/api/integration/jira/execute", body)
		if err != nil {
			return fmt.Errorf("failed to transition issue: %w", err)
		}

		var result map[string]interface{}
		json.Unmarshal(resp, &result)

		if result["success"].(bool) {
			fmt.Printf("✅ %s moved to %s\n", issueKey, status)
		} else {
			fmt.Printf("❌ %s\n", result["message"])
		}

		return nil
	},
}

var jiraCommentCmd = &cobra.Command{
	Use:   "comment [issue-key] [comment]",
	Short: "Add a comment to an issue",
	Args:  cobra.ExactArgs(2),
	RunE: func(cmd *cobra.Command, args []string) error {
		issueKey := args[0]
		comment := args[1]

		c := client.New()
		body, _ := json.Marshal(map[string]string{
			"action":   "addComment",
			"issueKey": issueKey,
			"comment":  comment,
		})

		resp, err := c.Post("/api/integration/jira/execute", body)
		if err != nil {
			return fmt.Errorf("failed to add comment: %w", err)
		}

		var result map[string]interface{}
		json.Unmarshal(resp, &result)

		if result["success"].(bool) {
			fmt.Printf("✅ Comment added to %s\n", issueKey)
		} else {
			fmt.Printf("❌ %s\n", result["message"])
		}

		return nil
	},
}

var jiraSearchCmd = &cobra.Command{
	Use:   "search [jql]",
	Short: "Search issues with JQL",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		jql := args[0]
		maxResults, _ := cmd.Flags().GetInt("max")

		c := client.New()
		body, _ := json.Marshal(map[string]interface{}{
			"action":     "searchIssues",
			"jql":        jql,
			"maxResults": maxResults,
		})

		resp, err := c.Post("/api/integration/jira/execute", body)
		if err != nil {
			return fmt.Errorf("failed to search issues: %w", err)
		}

		var result map[string]interface{}
		json.Unmarshal(resp, &result)

		if result["success"].(bool) {
			data := result["data"].(map[string]interface{})
			issues := data["issues"].([]interface{})
			
			fmt.Printf("🔍 Found %d issues:\n\n", len(issues))
			for _, i := range issues {
				issue := i.(map[string]interface{})
				fields := issue["fields"].(map[string]interface{})
				fmt.Printf("  %s: %s\n", issue["key"], fields["summary"])
				fmt.Printf("    Status: %v | Type: %v\n", 
					getNestedString(fields, "status", "name"),
					getNestedString(fields, "issuetype", "name"))
			}
		} else {
			fmt.Printf("❌ %s\n", result["message"])
		}

		return nil
	},
}

func getNestedString(m map[string]interface{}, keys ...string) string {
	current := m
	for i, key := range keys {
		if val, ok := current[key]; ok {
			if i == len(keys)-1 {
				if str, ok := val.(string); ok {
					return str
				}
				return fmt.Sprintf("%v", val)
			}
			if nested, ok := val.(map[string]interface{}); ok {
				current = nested
			} else {
				return ""
			}
		} else {
			return ""
		}
	}
	return ""
}

func init() {
	rootCmd.AddCommand(jiraCmd)
	jiraCmd.AddCommand(jiraGetCmd)
	jiraCmd.AddCommand(jiraCreateCmd)
	jiraCmd.AddCommand(jiraTransitionCmd)
	jiraCmd.AddCommand(jiraCommentCmd)
	jiraCmd.AddCommand(jiraSearchCmd)

	// Create flags
	jiraCreateCmd.Flags().StringP("project", "p", "", "Project key (required)")
	jiraCreateCmd.Flags().StringP("type", "t", "Task", "Issue type")
	jiraCreateCmd.Flags().StringP("summary", "s", "", "Issue summary (required)")
	jiraCreateCmd.Flags().StringP("description", "d", "", "Issue description")
	jiraCreateCmd.MarkFlagRequired("project")
	jiraCreateCmd.MarkFlagRequired("summary")

	// Search flags
	jiraSearchCmd.Flags().IntP("max", "m", 20, "Maximum results")
}

