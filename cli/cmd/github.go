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

// GitHub DTOs
type GitHubUser struct {
	Login       string `json:"login"`
	Name        string `json:"name"`
	PublicRepos int    `json:"repos"`
}

type GitHubRepo struct {
	Name          string `json:"name"`
	FullName      string `json:"fullName"`
	Owner         string `json:"owner"`
	Description   string `json:"description"`
	HtmlUrl       string `json:"htmlUrl"`
	IsPrivate     bool   `json:"private"`
	IsFork        bool   `json:"fork"`
	Language      string `json:"language"`
	Stars         int    `json:"stars"`
	Forks         int    `json:"forks"`
	OpenIssues    int    `json:"openIssues"`
	DefaultBranch string `json:"defaultBranch"`
	UpdatedAt     string `json:"updatedAt"`
}

type GitHubPR struct {
	Number         int     `json:"number"`
	Title          string  `json:"title"`
	State          string  `json:"state"`
	Author         string  `json:"author"`
	HeadBranch     string  `json:"headBranch"`
	BaseBranch     string  `json:"baseBranch"`
	Body           string  `json:"body"`
	HtmlUrl        string  `json:"htmlUrl"`
	Merged         bool    `json:"merged"`
	Draft          bool    `json:"draft"`
	Mergeable      *bool   `json:"mergeable"`
	MergeableState string  `json:"mergeableState"`
	CreatedAt      string  `json:"createdAt"`
	UpdatedAt      string  `json:"updatedAt"`
}

type GitHubPRFile struct {
	Filename  string `json:"filename"`
	Status    string `json:"status"`
	Additions int    `json:"additions"`
	Deletions int    `json:"deletions"`
	Changes   int    `json:"changes"`
}

type MergeStatus struct {
	CanMerge       bool           `json:"canMerge"`
	HasConflicts   bool           `json:"hasConflicts"`
	Status         string         `json:"status"`
	MergeableState string         `json:"mergeableState"`
	Files          []GitHubPRFile `json:"files"`
	HeadBranch     string         `json:"headBranch"`
	BaseBranch     string         `json:"baseBranch"`
}

type MergeResult struct {
	Success bool        `json:"success"`
	Message string      `json:"message"`
	Status  MergeStatus `json:"status"`
}

type GitHubIssue struct {
	Number    int      `json:"number"`
	Title     string   `json:"title"`
	State     string   `json:"state"`
	Author    string   `json:"author"`
	Body      string   `json:"body"`
	HtmlUrl   string   `json:"htmlUrl"`
	Labels    []string `json:"labels"`
	CreatedAt string   `json:"createdAt"`
	UpdatedAt string   `json:"updatedAt"`
}

type GitHubBranch struct {
	Name        string `json:"name"`
	Sha         string `json:"sha"`
	IsProtected bool   `json:"protected"`
}

type GitHubCommit struct {
	Sha         string `json:"sha"`
	Message     string `json:"message"`
	AuthorName  string `json:"authorName"`
	AuthorLogin string `json:"authorLogin"`
	Date        string `json:"date"`
	HtmlUrl     string `json:"htmlUrl"`
}

type GitHubStatus struct {
	Available bool   `json:"available"`
	User      string `json:"user"`
	Name      string `json:"name"`
	Message   string `json:"message"`
}

var ghCmd = &cobra.Command{
	Use:   "gh",
	Short: "GitHub integration commands",
	Long: `Interact with GitHub for repository management, PRs, and issues.

Requires environment variable:
  GITHUB_TOKEN - Your GitHub personal access token

Get your token at: https://github.com/settings/tokens
Required scopes: repo, read:user

Examples:
  sdlc gh status                     # Check GitHub connection
  sdlc gh repos                      # List your repositories
  sdlc gh repo owner/repo            # Get repo details
  sdlc gh prs owner/repo             # List pull requests
  sdlc gh pr create owner/repo       # Create a pull request
  sdlc gh issues owner/repo          # List issues
  sdlc gh issue create owner/repo    # Create an issue`,
	Run: func(cmd *cobra.Command, args []string) {
		cmd.Help()
	},
}

var ghStatusCmd = &cobra.Command{
	Use:   "status",
	Short: "Check GitHub connection status",
	Run:   runGHStatus,
}

var ghReposCmd = &cobra.Command{
	Use:   "repos",
	Short: "List your repositories",
	Run:   runGHRepos,
}

var ghRepoCmd = &cobra.Command{
	Use:   "repo <owner/repo>",
	Short: "Get repository details",
	Args:  cobra.ExactArgs(1),
	Run:   runGHRepo,
}

var ghPRsCmd = &cobra.Command{
	Use:   "prs <owner/repo>",
	Short: "List pull requests",
	Args:  cobra.ExactArgs(1),
	Run:   runGHPRs,
}

var ghPRCmd = &cobra.Command{
	Use:   "pr <owner/repo> <number>",
	Short: "Get pull request details",
	Args:  cobra.ExactArgs(2),
	Run:   runGHPR,
}

var ghPRCreateCmd = &cobra.Command{
	Use:   "pr-create <owner/repo> <title>",
	Short: "Create a pull request",
	Args:  cobra.MinimumNArgs(2),
	Run:   runGHPRCreate,
}

var ghPRMergeCmd = &cobra.Command{
	Use:   "pr-merge <owner/repo> <number>",
	Short: "Merge a pull request",
	Args:  cobra.ExactArgs(2),
	Run:   runGHPRMerge,
}

var ghPRStatusCmd = &cobra.Command{
	Use:   "pr-status <owner/repo> <number>",
	Short: "Check merge status and conflicts for a PR",
	Args:  cobra.ExactArgs(2),
	Run:   runGHPRStatus,
}

var ghIssuesCmd = &cobra.Command{
	Use:   "issues <owner/repo>",
	Short: "List issues",
	Args:  cobra.ExactArgs(1),
	Run:   runGHIssues,
}

var ghIssueCreateCmd = &cobra.Command{
	Use:   "issue-create <owner/repo> <title>",
	Short: "Create an issue",
	Args:  cobra.MinimumNArgs(2),
	Run:   runGHIssueCreate,
}

var ghIssueCloseCmd = &cobra.Command{
	Use:   "issue-close <owner/repo> <number>",
	Short: "Close an issue",
	Args:  cobra.ExactArgs(2),
	Run:   runGHIssueClose,
}

var ghCommentCmd = &cobra.Command{
	Use:   "comment <owner/repo> <number> <comment>",
	Short: "Add a comment to an issue or PR",
	Args:  cobra.MinimumNArgs(3),
	Run:   runGHComment,
}

var ghBranchesCmd = &cobra.Command{
	Use:   "branches <owner/repo>",
	Short: "List branches",
	Args:  cobra.ExactArgs(1),
	Run:   runGHBranches,
}

var ghCommitsCmd = &cobra.Command{
	Use:   "commits <owner/repo>",
	Short: "List recent commits",
	Args:  cobra.ExactArgs(1),
	Run:   runGHCommits,
}

var (
	ghRepoType    string
	ghState       string
	ghLimit       int
	ghHead        string
	ghBase        string
	ghBody        string
	ghLabels      []string
	ghBranch      string
)

func init() {
	rootCmd.AddCommand(ghCmd)
	
	ghCmd.AddCommand(ghStatusCmd)
	ghCmd.AddCommand(ghReposCmd)
	ghCmd.AddCommand(ghRepoCmd)
	ghCmd.AddCommand(ghPRsCmd)
	ghCmd.AddCommand(ghPRCmd)
	ghCmd.AddCommand(ghPRCreateCmd)
	ghCmd.AddCommand(ghPRMergeCmd)
	ghCmd.AddCommand(ghPRStatusCmd)
	ghCmd.AddCommand(ghIssuesCmd)
	ghCmd.AddCommand(ghIssueCreateCmd)
	ghCmd.AddCommand(ghIssueCloseCmd)
	ghCmd.AddCommand(ghCommentCmd)
	ghCmd.AddCommand(ghBranchesCmd)
	ghCmd.AddCommand(ghCommitsCmd)
	
	// Flags for repos
	ghReposCmd.Flags().StringVarP(&ghRepoType, "type", "t", "all", "Type: all, owner, public, private, member")
	ghReposCmd.Flags().IntVarP(&ghLimit, "limit", "l", 20, "Max repos to show")
	
	// Flags for PRs and issues
	ghPRsCmd.Flags().StringVarP(&ghState, "state", "s", "open", "State: open, closed, all")
	ghPRsCmd.Flags().IntVarP(&ghLimit, "limit", "l", 20, "Max PRs to show")
	
	ghIssuesCmd.Flags().StringVarP(&ghState, "state", "s", "open", "State: open, closed, all")
	ghIssuesCmd.Flags().IntVarP(&ghLimit, "limit", "l", 20, "Max issues to show")
	
	// Flags for PR create
	ghPRCreateCmd.Flags().StringVarP(&ghHead, "head", "H", "", "Head branch (required)")
	ghPRCreateCmd.Flags().StringVarP(&ghBase, "base", "B", "main", "Base branch")
	ghPRCreateCmd.Flags().StringVarP(&ghBody, "body", "b", "", "PR description")
	ghPRCreateCmd.MarkFlagRequired("head")
	
	// Flags for issue create
	ghIssueCreateCmd.Flags().StringVarP(&ghBody, "body", "b", "", "Issue description")
	ghIssueCreateCmd.Flags().StringSliceVarP(&ghLabels, "labels", "l", nil, "Labels (comma-separated)")
	
	// Flags for commits
	ghCommitsCmd.Flags().StringVarP(&ghBranch, "branch", "b", "", "Branch name")
	ghCommitsCmd.Flags().IntVarP(&ghLimit, "limit", "l", 10, "Max commits to show")
	
	// Flags for branches
	ghBranchesCmd.Flags().IntVarP(&ghLimit, "limit", "l", 20, "Max branches to show")
}

func runGHStatus(cmd *cobra.Command, args []string) {
	backendURL := getBackendURL()
	
	resp, err := ghRequest("GET", backendURL+"/api/github/status", nil)
	if err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error: %v\n", err)
		os.Exit(1)
	}
	
	var status GitHubStatus
	if err := json.Unmarshal(resp, &status); err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error parsing response: %v\n", err)
		os.Exit(1)
	}
	
	if status.Available {
		fmt.Println("✅ GitHub integration is connected!")
		fmt.Printf("   👤 User: %s\n", status.User)
		if status.Name != "" && status.Name != status.User {
			fmt.Printf("   📛 Name: %s\n", status.Name)
		}
	} else {
		fmt.Println("❌ GitHub integration is not configured")
		fmt.Println("\nTo configure GitHub:")
		fmt.Println("  1. Go to: https://github.com/settings/tokens")
		fmt.Println("  2. Generate new token (classic)")
		fmt.Println("  3. Select scopes: repo, read:user")
		fmt.Println("  4. Add to .env: GITHUB_TOKEN=your_token_here")
	}
}

func runGHRepos(cmd *cobra.Command, args []string) {
	backendURL := getBackendURL()
	
	url := fmt.Sprintf("%s/api/github/repos?type=%s&limit=%d", backendURL, ghRepoType, ghLimit)
	
	resp, err := ghRequest("GET", url, nil)
	if err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error: %v\n", err)
		os.Exit(1)
	}
	
	var repos []GitHubRepo
	if err := json.Unmarshal(resp, &repos); err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error parsing response: %v\n", err)
		os.Exit(1)
	}
	
	if len(repos) == 0 {
		fmt.Println("No repositories found")
		return
	}
	
	fmt.Println("═══════════════════════════════════════════════════════════════")
	fmt.Println("📦 Your Repositories")
	fmt.Println("═══════════════════════════════════════════════════════════════")
	
	for _, repo := range repos {
		visibility := "🌐"
		if repo.IsPrivate {
			visibility = "🔒"
		}
		
		fmt.Printf("\n%s %s", visibility, repo.FullName)
		if repo.IsFork {
			fmt.Print(" (fork)")
		}
		fmt.Println()
		
		if repo.Description != "" {
			fmt.Printf("   %s\n", truncate(repo.Description, 60))
		}
		
		fmt.Printf("   ⭐ %d  🍴 %d  📋 %d issues", repo.Stars, repo.Forks, repo.OpenIssues)
		if repo.Language != "" {
			fmt.Printf("  🔤 %s", repo.Language)
		}
		fmt.Println()
	}
	
	fmt.Printf("\n📊 Total: %d repositories\n", len(repos))
}

func runGHRepo(cmd *cobra.Command, args []string) {
	owner, repo := parseOwnerRepo(args[0])
	backendURL := getBackendURL()
	
	url := fmt.Sprintf("%s/api/github/repos/%s/%s", backendURL, owner, repo)
	
	resp, err := ghRequest("GET", url, nil)
	if err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error: %v\n", err)
		os.Exit(1)
	}
	
	var r GitHubRepo
	if err := json.Unmarshal(resp, &r); err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error parsing response: %v\n", err)
		os.Exit(1)
	}
	
	visibility := "Public 🌐"
	if r.IsPrivate {
		visibility = "Private 🔒"
	}
	
	fmt.Println("═══════════════════════════════════════════════════════════════")
	fmt.Printf("📦 %s\n", r.FullName)
	fmt.Println("═══════════════════════════════════════════════════════════════")
	
	if r.Description != "" {
		fmt.Printf("\n📄 %s\n", r.Description)
	}
	
	fmt.Printf("\n%s\n", visibility)
	fmt.Printf("🌿 Default branch: %s\n", r.DefaultBranch)
	if r.Language != "" {
		fmt.Printf("🔤 Language: %s\n", r.Language)
	}
	
	fmt.Printf("\n⭐ Stars: %d\n", r.Stars)
	fmt.Printf("🍴 Forks: %d\n", r.Forks)
	fmt.Printf("📋 Open Issues: %d\n", r.OpenIssues)
	
	fmt.Printf("\n🔗 %s\n", r.HtmlUrl)
}

func runGHPRs(cmd *cobra.Command, args []string) {
	owner, repo := parseOwnerRepo(args[0])
	backendURL := getBackendURL()
	
	url := fmt.Sprintf("%s/api/github/repos/%s/%s/pulls?state=%s&limit=%d", 
			backendURL, owner, repo, ghState, ghLimit)
	
	resp, err := ghRequest("GET", url, nil)
	if err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error: %v\n", err)
		os.Exit(1)
	}
	
	var prs []GitHubPR
	if err := json.Unmarshal(resp, &prs); err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error parsing response: %v\n", err)
		os.Exit(1)
	}
	
	if len(prs) == 0 {
		fmt.Printf("No %s pull requests in %s/%s\n", ghState, owner, repo)
		return
	}
	
	fmt.Println("═══════════════════════════════════════════════════════════════")
	fmt.Printf("🔀 Pull Requests in %s/%s\n", owner, repo)
	fmt.Println("═══════════════════════════════════════════════════════════════")
	
	for _, pr := range prs {
		stateIcon := getPRStateIcon(pr.State, pr.Merged, pr.Draft)
		fmt.Printf("\n%s #%d %s\n", stateIcon, pr.Number, pr.Title)
		fmt.Printf("   %s → %s  by @%s\n", pr.HeadBranch, pr.BaseBranch, pr.Author)
	}
	
	fmt.Printf("\n📊 Total: %d pull requests\n", len(prs))
}

func runGHPR(cmd *cobra.Command, args []string) {
	owner, repo := parseOwnerRepo(args[0])
	number := parseNumber(args[1])
	backendURL := getBackendURL()
	
	url := fmt.Sprintf("%s/api/github/repos/%s/%s/pulls/%d", backendURL, owner, repo, number)
	
	resp, err := ghRequest("GET", url, nil)
	if err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error: %v\n", err)
		os.Exit(1)
	}
	
	var pr GitHubPR
	if err := json.Unmarshal(resp, &pr); err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error parsing response: %v\n", err)
		os.Exit(1)
	}
	
	stateIcon := getPRStateIcon(pr.State, pr.Merged, pr.Draft)
	
	fmt.Println("═══════════════════════════════════════════════════════════════")
	fmt.Printf("%s PR #%d\n", stateIcon, pr.Number)
	fmt.Println("═══════════════════════════════════════════════════════════════")
	
	fmt.Printf("\n📝 %s\n", pr.Title)
	fmt.Printf("👤 Author: @%s\n", pr.Author)
	fmt.Printf("🌿 %s → %s\n", pr.HeadBranch, pr.BaseBranch)
	
	status := pr.State
	if pr.Merged {
		status = "merged"
	} else if pr.Draft {
		status = "draft"
	}
	fmt.Printf("📊 Status: %s\n", status)
	
	// Show merge status
	if pr.State == "open" && !pr.Draft {
		mergeStatus := "⏳ Checking..."
		if pr.Mergeable != nil {
			if *pr.Mergeable {
				mergeStatus = "✅ Can be merged"
			} else {
				mergeStatus = "❌ Has conflicts"
			}
		}
		if pr.MergeableState == "dirty" {
			mergeStatus = "❌ Has conflicts"
		} else if pr.MergeableState == "clean" {
			mergeStatus = "✅ Can be merged"
		} else if pr.MergeableState == "blocked" {
			mergeStatus = "🚫 Blocked (checks failing)"
		} else if pr.MergeableState == "behind" {
			mergeStatus = "⚠️  Behind base branch"
		}
		fmt.Printf("🔀 Merge: %s\n", mergeStatus)
	}
	
	if pr.Body != "" {
		fmt.Println("\n📄 Description:")
		fmt.Println("───────────────────────────────────────────────────────────────")
		fmt.Println(pr.Body)
	}
	
	fmt.Printf("\n🔗 %s\n", pr.HtmlUrl)
	
	// Hint for checking conflicts
	if pr.MergeableState == "dirty" || (pr.Mergeable != nil && !*pr.Mergeable) {
		fmt.Printf("\n💡 Tip: Run 'sdlc gh pr-status %s/%s %d' to see conflict details\n", owner, repo, number)
	}
}

func runGHPRCreate(cmd *cobra.Command, args []string) {
	owner, repo := parseOwnerRepo(args[0])
	title := strings.Join(args[1:], " ")
	backendURL := getBackendURL()
	
	request := map[string]string{
		"title": title,
		"head":  ghHead,
		"base":  ghBase,
		"body":  ghBody,
	}
	
	jsonData, _ := json.Marshal(request)
	
	url := fmt.Sprintf("%s/api/github/repos/%s/%s/pulls", backendURL, owner, repo)
	resp, err := ghRequest("POST", url, jsonData)
	if err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error: %v\n", err)
		os.Exit(1)
	}
	
	var pr GitHubPR
	if err := json.Unmarshal(resp, &pr); err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error parsing response: %v\n", err)
		os.Exit(1)
	}
	
	fmt.Println("✅ Pull request created!")
	fmt.Printf("\n🔀 #%d: %s\n", pr.Number, pr.Title)
	fmt.Printf("   %s → %s\n", pr.HeadBranch, pr.BaseBranch)
	fmt.Printf("\n🔗 %s\n", pr.HtmlUrl)
}

func runGHPRMerge(cmd *cobra.Command, args []string) {
	owner, repo := parseOwnerRepo(args[0])
	number := parseNumber(args[1])
	backendURL := getBackendURL()
	
	url := fmt.Sprintf("%s/api/github/repos/%s/%s/pulls/%d/merge", backendURL, owner, repo, number)
	
	resp, err := ghRequest("POST", url, []byte("{}"))
	if err != nil {
		// Try to parse merge result for conflict info
		fmt.Fprintf(os.Stderr, "❌ Error: %v\n", err)
		os.Exit(1)
	}
	
	var result MergeResult
	if err := json.Unmarshal(resp, &result); err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error parsing response: %v\n", err)
		os.Exit(1)
	}
	
	if result.Success {
		fmt.Printf("✅ PR #%d merged successfully!\n", number)
	} else {
		fmt.Printf("❌ Cannot merge PR #%d\n", number)
		fmt.Printf("   Reason: %s\n", result.Message)
		
		if result.Status.HasConflicts {
			fmt.Println("\n⚠️  This PR has merge conflicts!")
			fmt.Println("\n📄 Files in this PR:")
			for _, file := range result.Status.Files {
				fmt.Printf("   • %s (+%d/-%d)\n", file.Filename, file.Additions, file.Deletions)
			}
			
			fmt.Println("\n🔧 To resolve conflicts locally:")
			fmt.Printf("   git checkout %s\n", result.Status.BaseBranch)
			fmt.Println("   git pull")
			fmt.Printf("   git merge origin/%s\n", result.Status.HeadBranch)
			fmt.Println("   # Resolve conflicts in your editor")
			fmt.Println("   git add .")
			fmt.Println("   git commit")
			fmt.Println("   git push")
		}
		os.Exit(1)
	}
}

func runGHPRStatus(cmd *cobra.Command, args []string) {
	owner, repo := parseOwnerRepo(args[0])
	number := parseNumber(args[1])
	backendURL := getBackendURL()
	
	url := fmt.Sprintf("%s/api/github/repos/%s/%s/pulls/%d/merge-status", backendURL, owner, repo, number)
	
	resp, err := ghRequest("GET", url, nil)
	if err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error: %v\n", err)
		os.Exit(1)
	}
	
	var status MergeStatus
	if err := json.Unmarshal(resp, &status); err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error parsing response: %v\n", err)
		os.Exit(1)
	}
	
	fmt.Println("═══════════════════════════════════════════════════════════════")
	fmt.Printf("🔀 Merge Status for PR #%d\n", number)
	fmt.Println("═══════════════════════════════════════════════════════════════")
	
	fmt.Printf("\n🌿 %s → %s\n", status.HeadBranch, status.BaseBranch)
	
	// Show merge status
	switch status.Status {
	case "clean":
		fmt.Println("\n✅ Status: Ready to merge (no conflicts)")
	case "has_conflicts":
		fmt.Println("\n❌ Status: Has merge conflicts")
	case "checking":
		fmt.Println("\n⏳ Status: GitHub is checking mergeability...")
	case "already_merged":
		fmt.Println("\n🟣 Status: Already merged")
	case "closed":
		fmt.Println("\n🔴 Status: PR is closed")
	default:
		fmt.Printf("\n⚠️  Status: %s\n", status.Status)
	}
	
	if status.MergeableState != "" && status.MergeableState != status.Status {
		fmt.Printf("   Mergeable state: %s\n", status.MergeableState)
	}
	
	// Show files
	if len(status.Files) > 0 {
		fmt.Printf("\n📄 Files changed (%d):\n", len(status.Files))
		for _, file := range status.Files {
			statusIcon := "📝"
			switch file.Status {
			case "added":
				statusIcon = "➕"
			case "removed":
				statusIcon = "➖"
			case "modified":
				statusIcon = "✏️"
			case "renamed":
				statusIcon = "📛"
			}
			fmt.Printf("   %s %s (+%d/-%d)\n", statusIcon, file.Filename, file.Additions, file.Deletions)
		}
	}
	
	// Show resolution instructions if conflicts
	if status.HasConflicts {
		fmt.Println("\n🔧 To resolve conflicts locally:")
		fmt.Printf("   git checkout %s\n", status.BaseBranch)
		fmt.Println("   git pull")
		fmt.Printf("   git merge origin/%s\n", status.HeadBranch)
		fmt.Println("   # Resolve conflicts in your editor")
		fmt.Println("   git add .")
		fmt.Println("   git commit")
		fmt.Println("   git push")
	}
}

func runGHIssues(cmd *cobra.Command, args []string) {
	owner, repo := parseOwnerRepo(args[0])
	backendURL := getBackendURL()
	
	url := fmt.Sprintf("%s/api/github/repos/%s/%s/issues?state=%s&limit=%d", 
			backendURL, owner, repo, ghState, ghLimit)
	
	resp, err := ghRequest("GET", url, nil)
	if err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error: %v\n", err)
		os.Exit(1)
	}
	
	var issues []GitHubIssue
	if err := json.Unmarshal(resp, &issues); err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error parsing response: %v\n", err)
		os.Exit(1)
	}
	
	if len(issues) == 0 {
		fmt.Printf("No %s issues in %s/%s\n", ghState, owner, repo)
		return
	}
	
	fmt.Println("═══════════════════════════════════════════════════════════════")
	fmt.Printf("📋 Issues in %s/%s\n", owner, repo)
	fmt.Println("═══════════════════════════════════════════════════════════════")
	
	for _, issue := range issues {
		stateIcon := "🟢"
		if issue.State == "closed" {
			stateIcon = "🟣"
		}
		
		fmt.Printf("\n%s #%d %s\n", stateIcon, issue.Number, issue.Title)
		fmt.Printf("   by @%s", issue.Author)
		if len(issue.Labels) > 0 {
			fmt.Printf("  🏷️ %s", strings.Join(issue.Labels, ", "))
		}
		fmt.Println()
	}
	
	fmt.Printf("\n📊 Total: %d issues\n", len(issues))
}

func runGHIssueCreate(cmd *cobra.Command, args []string) {
	owner, repo := parseOwnerRepo(args[0])
	title := strings.Join(args[1:], " ")
	backendURL := getBackendURL()
	
	request := map[string]interface{}{
		"title": title,
		"body":  ghBody,
	}
	if len(ghLabels) > 0 {
		request["labels"] = ghLabels
	}
	
	jsonData, _ := json.Marshal(request)
	
	url := fmt.Sprintf("%s/api/github/repos/%s/%s/issues", backendURL, owner, repo)
	resp, err := ghRequest("POST", url, jsonData)
	if err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error: %v\n", err)
		os.Exit(1)
	}
	
	var issue GitHubIssue
	if err := json.Unmarshal(resp, &issue); err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error parsing response: %v\n", err)
		os.Exit(1)
	}
	
	fmt.Println("✅ Issue created!")
	fmt.Printf("\n📋 #%d: %s\n", issue.Number, issue.Title)
	fmt.Printf("\n🔗 %s\n", issue.HtmlUrl)
}

func runGHIssueClose(cmd *cobra.Command, args []string) {
	owner, repo := parseOwnerRepo(args[0])
	number := parseNumber(args[1])
	backendURL := getBackendURL()
	
	url := fmt.Sprintf("%s/api/github/repos/%s/%s/issues/%d/close", backendURL, owner, repo, number)
	
	_, err := ghRequest("POST", url, nil)
	if err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error: %v\n", err)
		os.Exit(1)
	}
	
	fmt.Printf("✅ Issue #%d closed\n", number)
}

func runGHComment(cmd *cobra.Command, args []string) {
	owner, repo := parseOwnerRepo(args[0])
	number := parseNumber(args[1])
	comment := strings.Join(args[2:], " ")
	backendURL := getBackendURL()
	
	request := map[string]string{"body": comment}
	jsonData, _ := json.Marshal(request)
	
	url := fmt.Sprintf("%s/api/github/repos/%s/%s/issues/%d/comments", backendURL, owner, repo, number)
	
	_, err := ghRequest("POST", url, jsonData)
	if err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error: %v\n", err)
		os.Exit(1)
	}
	
	fmt.Printf("✅ Comment added to #%d\n", number)
}

func runGHBranches(cmd *cobra.Command, args []string) {
	owner, repo := parseOwnerRepo(args[0])
	backendURL := getBackendURL()
	
	url := fmt.Sprintf("%s/api/github/repos/%s/%s/branches?limit=%d", backendURL, owner, repo, ghLimit)
	
	resp, err := ghRequest("GET", url, nil)
	if err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error: %v\n", err)
		os.Exit(1)
	}
	
	var branches []GitHubBranch
	if err := json.Unmarshal(resp, &branches); err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error parsing response: %v\n", err)
		os.Exit(1)
	}
	
	if len(branches) == 0 {
		fmt.Printf("No branches found in %s/%s\n", owner, repo)
		return
	}
	
	fmt.Println("═══════════════════════════════════════════════════════════════")
	fmt.Printf("🌿 Branches in %s/%s\n", owner, repo)
	fmt.Println("═══════════════════════════════════════════════════════════════")
	
	for _, branch := range branches {
		protected := ""
		if branch.IsProtected {
			protected = " 🔒"
		}
		fmt.Printf("\n  • %s%s\n", branch.Name, protected)
		fmt.Printf("    %.7s\n", branch.Sha)
	}
	
	fmt.Printf("\n📊 Total: %d branches\n", len(branches))
}

func runGHCommits(cmd *cobra.Command, args []string) {
	owner, repo := parseOwnerRepo(args[0])
	backendURL := getBackendURL()
	
	url := fmt.Sprintf("%s/api/github/repos/%s/%s/commits?limit=%d", backendURL, owner, repo, ghLimit)
	if ghBranch != "" {
		url += "&branch=" + ghBranch
	}
	
	resp, err := ghRequest("GET", url, nil)
	if err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error: %v\n", err)
		os.Exit(1)
	}
	
	var commits []GitHubCommit
	if err := json.Unmarshal(resp, &commits); err != nil {
		fmt.Fprintf(os.Stderr, "❌ Error parsing response: %v\n", err)
		os.Exit(1)
	}
	
	if len(commits) == 0 {
		fmt.Printf("No commits found in %s/%s\n", owner, repo)
		return
	}
	
	fmt.Println("═══════════════════════════════════════════════════════════════")
	fmt.Printf("📝 Recent Commits in %s/%s\n", owner, repo)
	fmt.Println("═══════════════════════════════════════════════════════════════")
	
	for _, commit := range commits {
		// Get first line of commit message
		message := strings.Split(commit.Message, "\n")[0]
		
		author := commit.AuthorName
		if commit.AuthorLogin != "" {
			author = "@" + commit.AuthorLogin
		}
		
		fmt.Printf("\n  %.7s %s\n", commit.Sha, truncate(message, 50))
		fmt.Printf("         by %s\n", author)
	}
	
	fmt.Printf("\n📊 Showing %d commits\n", len(commits))
}

// Helper functions

func ghRequest(method, url string, body []byte) ([]byte, error) {
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

func parseOwnerRepo(s string) (string, string) {
	parts := strings.SplitN(s, "/", 2)
	if len(parts) != 2 {
		fmt.Fprintf(os.Stderr, "❌ Invalid format. Use: owner/repo\n")
		os.Exit(1)
	}
	return parts[0], parts[1]
}

func parseNumber(s string) int {
	var n int
	_, err := fmt.Sscanf(s, "%d", &n)
	if err != nil {
		fmt.Fprintf(os.Stderr, "❌ Invalid number: %s\n", s)
		os.Exit(1)
	}
	return n
}

func getPRStateIcon(state string, merged bool, draft bool) string {
	if merged {
		return "🟣"
	}
	if draft {
		return "⚪"
	}
	if state == "open" {
		return "🟢"
	}
	return "🔴"
}

