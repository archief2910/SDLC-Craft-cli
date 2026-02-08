package cmd

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/spf13/cobra"
)

// Jira DTOs
type JiraProject struct {
	ID   string `json:"id"`
	Key  string `json:"key"`
	Name string `json:"name"`
}

type JiraIssue struct {
	Key         string `json:"key"`
	Summary     string `json:"summary"`
	Status      string `json:"status"`
	Priority    string `json:"priority"`
	Assignee    string `json:"assignee"`
	Description string `json:"description"`
	Created     string `json:"created"`
	Updated     string `json:"updated"`
}

type JiraStatus struct {
	Available bool   `json:"available"`
	Message   string `json:"message"`
}

var jiraCmd = &cobra.Command{
	Use:   "jira",
	Short: "Jira integration commands",
	Long: `Interact with Jira for issue tracking and project management.

Requires environment variables:
  JIRA_URL    - Your Jira instance URL (e.g., https://yourcompany.atlassian.net)
  JIRA_EMAIL  - Your Jira email
  JIRA_TOKEN  - Your Jira API token (get from https://id.atlassian.com/manage/api-tokens)

Examples:
  sdlc jira status              # Check Jira connection
  sdlc jira projects            # List all projects
  sdlc jira issues PROJ         # List issues in project PROJ
  sdlc jira issue PROJ-123      # Get issue details
  sdlc jira create PROJ "Title" # Create new issue
  sdlc jira transition PROJ-123 "In Progress"  # Move issue
  sdlc jira comment PROJ-123 "My comment"      # Add comment`,
	Run: func(cmd *cobra.Command, args []string) {
		cmd.Help()
	},
}

var jiraStatusCmd = &cobra.Command{
	Use:   "status",
	Short: "Check Jira connection status",
	Run:   runJiraStatus,
}

var jiraProjectsCmd = &cobra.Command{
	Use:   "projects",
	Short: "List all Jira projects",
	Run:   runJiraProjects,
}

var jiraIssuesCmd = &cobra.Command{
	Use:   "issues <project-key>",
	Short: "List issues in a project",
	Args:  cobra.ExactArgs(1),
	Run:   runJiraIssues,
}

var jiraIssueCmd = &cobra.Command{
	Use:   "issue <issue-key>",
	Short: "Get details of a specific issue",
	Args:  cobra.ExactArgs(1),
	Run:   runJiraIssue,
}

var jiraCreateCmd = &cobra.Command{
	Use:   "create <project-key> <summary>",
	Short: "Create a new issue",
	Args:  cobra.MinimumNArgs(2),
	Run:   runJiraCreate,
}

var jiraTransitionCmd = &cobra.Command{
	Use:   "transition <issue-key> <status>",
	Short: "Transition an issue to a new status",
	Args:  cobra.ExactArgs(2),
	Run:   runJiraTransition,
}

var jiraCommentCmd = &cobra.Command{
	Use:   "comment <issue-key> <comment>",
	Short: "Add a comment to an issue",
	Args:  cobra.MinimumNArgs(2),
	Run:   runJiraComment,
}

var (
	jiraIssueType   string
	jiraDescription string
	jiraStatusFilter string
	jiraLimit       int
)

func init() {
	rootCmd.AddCommand(jiraCmd)
	
	jiraCmd.AddCommand(jiraStatusCmd)
	jiraCmd.AddCommand(jiraProjectsCmd)
	jiraCmd.AddCommand(jiraIssuesCmd)
	jiraCmd.AddCommand(jiraIssueCmd)
	jiraCmd.AddCommand(jiraCreateCmd)
	jiraCmd.AddCommand(jiraTransitionCmd)
	jiraCmd.AddCommand(jiraCommentCmd)
	
	// Flags for issues command
	jiraIssuesCmd.Flags().StringVarP(&jiraStatusFilter, "status", "s", "", "Filter by status (e.g., 'To Do', 'In Progress', 'Done')")
	jiraIssuesCmd.Flags().IntVarP(&jiraLimit, "limit", "l", 20, "Maximum number of issues to show")
	
	// Flags for create command
	jiraCreateCmd.Flags().StringVarP(&jiraIssueType, "type", "t", "Task", "Issue type (Task, Bug, Story, Epic)")
	jiraCreateCmd.Flags().StringVarP(&jiraDescription, "description", "d", "", "Issue description")
}

func getBackendURL() string {
	url := os.Getenv("BACKEND_URL")
	if url == "" {
		return "http://localhost:8080"
	}
	return url
}

func runJiraStatus(cmd *cobra.Command, args []string) {
	backendURL := getBackendURL()
	
	resp, err := jiraRequest("GET", backendURL+"/api/jira/status", nil)
	if err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error: %v\n", err)
		os.Exit(1)
	}
	
	var status JiraStatus
	if err := json.Unmarshal(resp, &status); err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error parsing response: %v\n", err)
		os.Exit(1)
	}
	
	if status.Available {
		fmt.Println("✅ Jira integration is connected and ready!")
	} else {
		fmt.Println("❌ Jira integration is not configured")
		fmt.Println("\nTo configure Jira, set these environment variables:")
		fmt.Println("  JIRA_URL    - Your Jira instance URL")
		fmt.Println("               (e.g., https://yourcompany.atlassian.net)")
		fmt.Println("  JIRA_EMAIL  - Your Jira email")
		fmt.Println("  JIRA_TOKEN  - Your Jira API token")
		fmt.Println("\nGet your API token at: https://id.atlassian.com/manage/api-tokens")
	}
}

func runJiraProjects(cmd *cobra.Command, args []string) {
	backendURL := getBackendURL()
	
	resp, err := jiraRequest("GET", backendURL+"/api/jira/projects", nil)
	if err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error: %v\n", err)
		os.Exit(1)
	}
	
	var projects []JiraProject
	if err := json.Unmarshal(resp, &projects); err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error parsing response: %v\n", err)
		os.Exit(1)
	}
	
	if len(projects) == 0 {
		fmt.Println("No projects found")
		return
	}
	
	fmt.Println("═══════════════════════════════════════════════════════════════")
	fmt.Println("📋 Jira Projects")
	fmt.Println("═══════════════════════════════════════════════════════════════")
	fmt.Printf("\n%-10s %-40s\n", "KEY", "NAME")
	fmt.Println("───────────────────────────────────────────────────────────────")
	
	for _, p := range projects {
		fmt.Printf("%-10s %-40s\n", p.Key, truncate(p.Name, 40))
	}
	
	fmt.Printf("\n📊 Total: %d projects\n", len(projects))
}

func runJiraIssues(cmd *cobra.Command, args []string) {
	projectKey := args[0]
	backendURL := getBackendURL()
	
	url := fmt.Sprintf("%s/api/jira/issues?project=%s&limit=%d", backendURL, projectKey, jiraLimit)
	if jiraStatusFilter != "" {
		url += "&status=" + jiraStatusFilter
	}
	
	resp, err := jiraRequest("GET", url, nil)
	if err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error: %v\n", err)
		os.Exit(1)
	}
	
	var issues []JiraIssue
	if err := json.Unmarshal(resp, &issues); err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error parsing response: %v\n", err)
		os.Exit(1)
	}
	
	if len(issues) == 0 {
		fmt.Printf("No issues found in project %s\n", projectKey)
		return
	}
	
	fmt.Println("═══════════════════════════════════════════════════════════════")
	fmt.Printf("📋 Issues in %s\n", projectKey)
	fmt.Println("═══════════════════════════════════════════════════════════════")
	
	for _, issue := range issues {
		statusIcon := getStatusIcon(issue.Status)
		priorityIcon := getPriorityIcon(issue.Priority)
		
		fmt.Printf("\n%s %s %s\n", statusIcon, issue.Key, priorityIcon)
		fmt.Printf("   %s\n", truncate(issue.Summary, 60))
		fmt.Printf("   Status: %-15s  Assignee: %s\n", issue.Status, issue.Assignee)
	}
	
	fmt.Printf("\n📊 Total: %d issues\n", len(issues))
}

func runJiraIssue(cmd *cobra.Command, args []string) {
	issueKey := args[0]
	backendURL := getBackendURL()
	
	resp, err := jiraRequest("GET", backendURL+"/api/jira/issues/"+issueKey, nil)
	if err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error: %v\n", err)
		os.Exit(1)
	}
	
	var issue JiraIssue
	if err := json.Unmarshal(resp, &issue); err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error parsing response: %v\n", err)
		os.Exit(1)
	}
	
	fmt.Println("═══════════════════════════════════════════════════════════════")
	fmt.Printf("🎫 %s\n", issue.Key)
	fmt.Println("═══════════════════════════════════════════════════════════════")
	
	fmt.Printf("\n📝 Summary: %s\n", issue.Summary)
	fmt.Printf("\n%s Status:   %s\n", getStatusIcon(issue.Status), issue.Status)
	fmt.Printf("%s Priority: %s\n", getPriorityIcon(issue.Priority), issue.Priority)
	fmt.Printf("👤 Assignee: %s\n", issue.Assignee)
	
	if issue.Description != "" {
		fmt.Println("\n📄 Description:")
		fmt.Println("───────────────────────────────────────────────────────────────")
		fmt.Println(issue.Description)
	}
	
	fmt.Printf("\n📅 Created: %s\n", formatJiraDate(issue.Created))
	fmt.Printf("📅 Updated: %s\n", formatJiraDate(issue.Updated))
}

func runJiraCreate(cmd *cobra.Command, args []string) {
	projectKey := args[0]
	summary := strings.Join(args[1:], " ")
	backendURL := getBackendURL()
	
	request := map[string]string{
		"project":     projectKey,
		"issueType":   jiraIssueType,
		"summary":     summary,
		"description": jiraDescription,
	}
	
	jsonData, _ := json.Marshal(request)
	
	resp, err := jiraRequest("POST", backendURL+"/api/jira/issues", jsonData)
	if err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error: %v\n", err)
		os.Exit(1)
	}
	
	var issue JiraIssue
	if err := json.Unmarshal(resp, &issue); err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error parsing response: %v\n", err)
		os.Exit(1)
	}
	
	fmt.Println("✅ Issue created successfully!")
	fmt.Printf("\n🎫 %s: %s\n", issue.Key, issue.Summary)
	fmt.Printf("   Type: %s | Status: %s\n", jiraIssueType, issue.Status)
}

func runJiraTransition(cmd *cobra.Command, args []string) {
	issueKey := args[0]
	status := args[1]
	backendURL := getBackendURL()
	
	request := map[string]string{
		"status": status,
	}
	
	jsonData, _ := json.Marshal(request)
	
	_, err := jiraRequest("POST", backendURL+"/api/jira/issues/"+issueKey+"/transition", jsonData)
	if err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error: %v\n", err)
		os.Exit(1)
	}
	
	fmt.Printf("✅ Issue %s transitioned to '%s'\n", issueKey, status)
}

func runJiraComment(cmd *cobra.Command, args []string) {
	issueKey := args[0]
	comment := strings.Join(args[1:], " ")
	backendURL := getBackendURL()
	
	request := map[string]string{
		"comment": comment,
	}
	
	jsonData, _ := json.Marshal(request)
	
	_, err := jiraRequest("POST", backendURL+"/api/jira/issues/"+issueKey+"/comment", jsonData)
	if err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error: %v\n", err)
		os.Exit(1)
	}
	
	fmt.Printf("✅ Comment added to %s\n", issueKey)
}

func jiraRequest(method, url string, body []byte) ([]byte, error) {
	client := &http.Client{Timeout: 30 * time.Second}
	
	var req *http.Request
	var err error
	
	if body != nil {
		req, err = http.NewRequest(method, url, bytes.NewBuffer(body))
	} else {
		req, err = http.NewRequest(method, url, nil)
	}
	
	if err != nil {
		return nil, err
	}
	
	req.Header.Set("Content-Type", "application/json")
	
	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("failed to connect to backend: %v", err)
	}
	defer resp.Body.Close()
	
	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}
	
	if resp.StatusCode >= 400 {
		// Try to parse error message
		var errResp map[string]interface{}
		if json.Unmarshal(respBody, &errResp) == nil {
			if msg, ok := errResp["message"].(string); ok {
				return nil, fmt.Errorf("%s", msg)
			}
		}
		return nil, fmt.Errorf("request failed with status %d", resp.StatusCode)
	}
	
	return respBody, nil
}

func getStatusIcon(status string) string {
	status = strings.ToLower(status)
	switch {
	case strings.Contains(status, "done") || strings.Contains(status, "closed") || strings.Contains(status, "resolved"):
		return "✅"
	case strings.Contains(status, "progress") || strings.Contains(status, "review"):
		return "🔄"
	case strings.Contains(status, "blocked"):
		return "🚫"
	default:
		return "📋"
	}
}

func getPriorityIcon(priority string) string {
	priority = strings.ToLower(priority)
	switch {
	case strings.Contains(priority, "highest") || strings.Contains(priority, "critical"):
		return "🔴"
	case strings.Contains(priority, "high"):
		return "🟠"
	case strings.Contains(priority, "medium"):
		return "🟡"
	case strings.Contains(priority, "low"):
		return "🟢"
	case strings.Contains(priority, "lowest"):
		return "⚪"
	default:
		return "⚪"
	}
}

func truncate(s string, maxLen int) string {
	if len(s) <= maxLen {
		return s
	}
	return s[:maxLen-3] + "..."
}

func formatJiraDate(dateStr string) string {
	// Jira dates are in ISO format: 2024-01-15T10:30:00.000+0000
	t, err := time.Parse("2006-01-02T15:04:05.000-0700", dateStr)
	if err != nil {
		// Try alternate format
		t, err = time.Parse(time.RFC3339, dateStr)
		if err != nil {
			return dateStr
		}
	}
	return t.Format("Jan 2, 2006 3:04 PM")
}

