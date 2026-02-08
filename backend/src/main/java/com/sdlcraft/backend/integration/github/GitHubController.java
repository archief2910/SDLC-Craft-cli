package com.sdlcraft.backend.integration.github;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for GitHub integration endpoints.
 */
@RestController
@RequestMapping("/api/github")
public class GitHubController {
    
    private final GitHubService gitHubService;
    
    public GitHubController(GitHubService gitHubService) {
        this.gitHubService = gitHubService;
    }
    
    /**
     * Check if GitHub is configured and available.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        if (!gitHubService.isAvailable()) {
            return ResponseEntity.ok(Map.of(
                    "available", false,
                    "message", "GitHub not configured. Set GITHUB_TOKEN"
            ));
        }
        
        try {
            GitHubService.GitHubUser user = gitHubService.getAuthenticatedUser();
            return ResponseEntity.ok(Map.of(
                    "available", true,
                    "user", user.login(),
                    "name", user.name() != null ? user.name() : user.login(),
                    "repos", user.publicRepos()
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "available", false,
                    "message", "GitHub authentication failed: " + e.getMessage()
            ));
        }
    }
    
    /**
     * List repositories.
     */
    @GetMapping("/repos")
    public ResponseEntity<List<GitHubService.GitHubRepo>> listRepos(
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(defaultValue = "30") int limit) {
        
        if (!gitHubService.isAvailable()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(gitHubService.listRepositories(type, limit));
    }
    
    /**
     * Get repository details.
     */
    @GetMapping("/repos/{owner}/{repo}")
    public ResponseEntity<GitHubService.GitHubRepo> getRepo(
            @PathVariable String owner,
            @PathVariable String repo) {
        
        if (!gitHubService.isAvailable()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(gitHubService.getRepository(owner, repo));
    }
    
    /**
     * List pull requests.
     */
    @GetMapping("/repos/{owner}/{repo}/pulls")
    public ResponseEntity<List<GitHubService.GitHubPR>> listPRs(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam(defaultValue = "open") String state,
            @RequestParam(defaultValue = "30") int limit) {
        
        if (!gitHubService.isAvailable()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(gitHubService.listPullRequests(owner, repo, state, limit));
    }
    
    /**
     * Get pull request details.
     */
    @GetMapping("/repos/{owner}/{repo}/pulls/{number}")
    public ResponseEntity<GitHubService.GitHubPR> getPR(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable int number) {
        
        if (!gitHubService.isAvailable()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(gitHubService.getPullRequest(owner, repo, number));
    }
    
    /**
     * Create a pull request.
     */
    @PostMapping("/repos/{owner}/{repo}/pulls")
    public ResponseEntity<GitHubService.GitHubPR> createPR(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestBody CreatePRRequest request) {
        
        if (!gitHubService.isAvailable()) {
            return ResponseEntity.badRequest().build();
        }
        
        GitHubService.GitHubPR pr = gitHubService.createPullRequest(
                owner, repo, request.title(), request.head(), request.base(), request.body());
        
        return ResponseEntity.ok(pr);
    }
    
    /**
     * Get merge status for a PR (check for conflicts).
     */
    @GetMapping("/repos/{owner}/{repo}/pulls/{number}/merge-status")
    public ResponseEntity<GitHubService.MergeStatus> getMergeStatus(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable int number) {
        
        if (!gitHubService.isAvailable()) {
            return ResponseEntity.badRequest().build();
        }
        
        return ResponseEntity.ok(gitHubService.getMergeStatus(owner, repo, number));
    }
    
    /**
     * Get files changed in a PR.
     */
    @GetMapping("/repos/{owner}/{repo}/pulls/{number}/files")
    public ResponseEntity<List<GitHubService.GitHubPRFile>> getPRFiles(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable int number) {
        
        if (!gitHubService.isAvailable()) {
            return ResponseEntity.badRequest().build();
        }
        
        return ResponseEntity.ok(gitHubService.getPullRequestFiles(owner, repo, number));
    }
    
    /**
     * Merge a pull request (with conflict checking).
     */
    @PostMapping("/repos/{owner}/{repo}/pulls/{number}/merge")
    public ResponseEntity<GitHubService.MergeResult> mergePR(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable int number,
            @RequestBody(required = false) MergePRRequest request) {
        
        if (!gitHubService.isAvailable()) {
            return ResponseEntity.badRequest().build();
        }
        
        GitHubService.MergeResult result = gitHubService.mergePullRequest(
                owner, repo, number, 
                request != null ? request.commitTitle() : null);
        
        if (result.success()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.status(409).body(result); // 409 Conflict
        }
    }
    
    /**
     * List issues.
     */
    @GetMapping("/repos/{owner}/{repo}/issues")
    public ResponseEntity<List<GitHubService.GitHubIssue>> listIssues(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam(defaultValue = "open") String state,
            @RequestParam(defaultValue = "30") int limit) {
        
        if (!gitHubService.isAvailable()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(gitHubService.listIssues(owner, repo, state, limit));
    }
    
    /**
     * Create an issue.
     */
    @PostMapping("/repos/{owner}/{repo}/issues")
    public ResponseEntity<GitHubService.GitHubIssue> createIssue(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestBody CreateIssueRequest request) {
        
        if (!gitHubService.isAvailable()) {
            return ResponseEntity.badRequest().build();
        }
        
        GitHubService.GitHubIssue issue = gitHubService.createIssue(
                owner, repo, request.title(), request.body(), request.labels());
        
        return ResponseEntity.ok(issue);
    }
    
    /**
     * Close an issue.
     */
    @PostMapping("/repos/{owner}/{repo}/issues/{number}/close")
    public ResponseEntity<Map<String, String>> closeIssue(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable int number) {
        
        if (!gitHubService.isAvailable()) {
            return ResponseEntity.badRequest().build();
        }
        
        gitHubService.closeIssue(owner, repo, number);
        return ResponseEntity.ok(Map.of("message", "Issue #" + number + " closed"));
    }
    
    /**
     * Add a comment to an issue or PR.
     */
    @PostMapping("/repos/{owner}/{repo}/issues/{number}/comments")
    public ResponseEntity<Map<String, String>> addComment(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable int number,
            @RequestBody CommentRequest request) {
        
        if (!gitHubService.isAvailable()) {
            return ResponseEntity.badRequest().build();
        }
        
        gitHubService.addComment(owner, repo, number, request.body());
        return ResponseEntity.ok(Map.of("message", "Comment added to #" + number));
    }
    
    /**
     * List branches.
     */
    @GetMapping("/repos/{owner}/{repo}/branches")
    public ResponseEntity<List<GitHubService.GitHubBranch>> listBranches(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam(defaultValue = "30") int limit) {
        
        if (!gitHubService.isAvailable()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(gitHubService.listBranches(owner, repo, limit));
    }
    
    /**
     * List commits.
     */
    @GetMapping("/repos/{owner}/{repo}/commits")
    public ResponseEntity<List<GitHubService.GitHubCommit>> listCommits(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam(required = false) String branch,
            @RequestParam(defaultValue = "20") int limit) {
        
        if (!gitHubService.isAvailable()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(gitHubService.listCommits(owner, repo, branch, limit));
    }
    
    // Request DTOs
    
    public record CreatePRRequest(
            String title,
            String head,
            String base,
            String body
    ) {}
    
    public record MergePRRequest(String commitTitle) {}
    
    public record CreateIssueRequest(
            String title,
            String body,
            List<String> labels
    ) {}
    
    public record CommentRequest(String body) {}
}

