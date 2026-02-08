package com.sdlcraft.backend.integration.github;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * GitHub Integration Service.
 * 
 * Provides integration with GitHub for:
 * - Listing repositories
 * - Managing pull requests
 * - Creating and listing issues
 * - Branch management
 * - Viewing commits
 */
@Service
public class GitHubService {
    
    private static final Logger logger = LoggerFactory.getLogger(GitHubService.class);
    private static final String GITHUB_API_URL = "https://api.github.com";
    
    private final String token;
    private final String username;
    private final RestTemplate restTemplate;
    private final boolean available;
    
    public GitHubService(
            @Value("${sdlcraft.integrations.github.token:}") String token,
            @Value("${sdlcraft.integrations.github.username:}") String username) {
        
        this.token = token != null ? token : "";
        this.username = username != null ? username : "";
        
        this.restTemplate = new RestTemplate();
        this.restTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
            setConnectTimeout(10000);
            setReadTimeout(30000);
        }});
        
        this.available = !this.token.isEmpty();
        
        if (available) {
            logger.info("✅ GitHub integration initialized for user: {}", 
                    this.username.isEmpty() ? "(token owner)" : this.username);
        } else {
            logger.warn("⚠️ GitHub integration not configured. Set GITHUB_TOKEN");
        }
    }
    
    /**
     * Check if GitHub integration is available.
     */
    public boolean isAvailable() {
        return available;
    }
    
    /**
     * Get authenticated user info.
     */
    public GitHubUser getAuthenticatedUser() {
        if (!available) {
            throw new GitHubException("GitHub not configured");
        }
        
        try {
            String url = GITHUB_API_URL + "/user";
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, createAuthEntity(null), Map.class);
            
            Map<String, Object> data = response.getBody();
            return new GitHubUser(
                    (String) data.get("login"),
                    (String) data.get("name"),
                    (String) data.get("email"),
                    (String) data.get("avatar_url"),
                    ((Number) data.get("public_repos")).intValue()
            );
            
        } catch (Exception e) {
            logger.error("Failed to get GitHub user", e);
            throw new GitHubException("Failed to get user: " + e.getMessage());
        }
    }
    
    /**
     * List repositories for authenticated user.
     */
    public List<GitHubRepo> listRepositories(String type, int limit) {
        if (!available) {
            throw new GitHubException("GitHub not configured");
        }
        
        try {
            String url = GITHUB_API_URL + "/user/repos?type=" + type + 
                    "&sort=updated&direction=desc&per_page=" + limit;
            
            ResponseEntity<List> response = restTemplate.exchange(
                    url, HttpMethod.GET, createAuthEntity(null), List.class);
            
            List<Map<String, Object>> repos = response.getBody();
            if (repos == null) return Collections.emptyList();
            
            return repos.stream()
                    .map(this::parseRepo)
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            logger.error("Failed to list GitHub repositories", e);
            throw new GitHubException("Failed to list repos: " + e.getMessage());
        }
    }
    
    /**
     * Get repository details.
     */
    public GitHubRepo getRepository(String owner, String repo) {
        if (!available) {
            throw new GitHubException("GitHub not configured");
        }
        
        try {
            String url = GITHUB_API_URL + "/repos/" + owner + "/" + repo;
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, createAuthEntity(null), Map.class);
            
            return parseRepo(response.getBody());
            
        } catch (Exception e) {
            logger.error("Failed to get GitHub repository: {}/{}", owner, repo, e);
            throw new GitHubException("Failed to get repo: " + e.getMessage());
        }
    }
    
    /**
     * List pull requests for a repository.
     */
    public List<GitHubPR> listPullRequests(String owner, String repo, String state, int limit) {
        if (!available) {
            throw new GitHubException("GitHub not configured");
        }
        
        try {
            String url = GITHUB_API_URL + "/repos/" + owner + "/" + repo + 
                    "/pulls?state=" + state + "&sort=updated&direction=desc&per_page=" + limit;
            
            ResponseEntity<List> response = restTemplate.exchange(
                    url, HttpMethod.GET, createAuthEntity(null), List.class);
            
            List<Map<String, Object>> prs = response.getBody();
            if (prs == null) return Collections.emptyList();
            
            return prs.stream()
                    .map(this::parsePR)
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            logger.error("Failed to list pull requests for {}/{}", owner, repo, e);
            throw new GitHubException("Failed to list PRs: " + e.getMessage());
        }
    }
    
    /**
     * Get pull request details.
     */
    public GitHubPR getPullRequest(String owner, String repo, int number) {
        if (!available) {
            throw new GitHubException("GitHub not configured");
        }
        
        try {
            String url = GITHUB_API_URL + "/repos/" + owner + "/" + repo + "/pulls/" + number;
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, createAuthEntity(null), Map.class);
            
            return parsePR(response.getBody());
            
        } catch (Exception e) {
            logger.error("Failed to get pull request {}/{} #{}", owner, repo, number, e);
            throw new GitHubException("Failed to get PR: " + e.getMessage());
        }
    }
    
    /**
     * Create a pull request.
     */
    public GitHubPR createPullRequest(String owner, String repo, String title, 
                                       String head, String base, String body) {
        if (!available) {
            throw new GitHubException("GitHub not configured");
        }
        
        try {
            String url = GITHUB_API_URL + "/repos/" + owner + "/" + repo + "/pulls";
            
            Map<String, Object> request = new HashMap<>();
            request.put("title", title);
            request.put("head", head);
            request.put("base", base);
            if (body != null && !body.isEmpty()) {
                request.put("body", body);
            }
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, createAuthEntity(request), Map.class);
            
            logger.info("Created PR in {}/{}: {}", owner, repo, title);
            return parsePR(response.getBody());
            
        } catch (Exception e) {
            logger.error("Failed to create pull request in {}/{}", owner, repo, e);
            throw new GitHubException("Failed to create PR: " + e.getMessage());
        }
    }
    
    /**
     * List issues for a repository.
     */
    public List<GitHubIssue> listIssues(String owner, String repo, String state, int limit) {
        if (!available) {
            throw new GitHubException("GitHub not configured");
        }
        
        try {
            String url = GITHUB_API_URL + "/repos/" + owner + "/" + repo + 
                    "/issues?state=" + state + "&sort=updated&direction=desc&per_page=" + limit;
            
            ResponseEntity<List> response = restTemplate.exchange(
                    url, HttpMethod.GET, createAuthEntity(null), List.class);
            
            List<Map<String, Object>> issues = response.getBody();
            if (issues == null) return Collections.emptyList();
            
            // Filter out pull requests (they're included in issues API)
            return issues.stream()
                    .filter(i -> !i.containsKey("pull_request"))
                    .map(this::parseIssue)
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            logger.error("Failed to list issues for {}/{}", owner, repo, e);
            throw new GitHubException("Failed to list issues: " + e.getMessage());
        }
    }
    
    /**
     * Create an issue.
     */
    public GitHubIssue createIssue(String owner, String repo, String title, 
                                    String body, List<String> labels) {
        if (!available) {
            throw new GitHubException("GitHub not configured");
        }
        
        try {
            String url = GITHUB_API_URL + "/repos/" + owner + "/" + repo + "/issues";
            
            Map<String, Object> request = new HashMap<>();
            request.put("title", title);
            if (body != null && !body.isEmpty()) {
                request.put("body", body);
            }
            if (labels != null && !labels.isEmpty()) {
                request.put("labels", labels);
            }
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, createAuthEntity(request), Map.class);
            
            logger.info("Created issue in {}/{}: {}", owner, repo, title);
            return parseIssue(response.getBody());
            
        } catch (Exception e) {
            logger.error("Failed to create issue in {}/{}", owner, repo, e);
            throw new GitHubException("Failed to create issue: " + e.getMessage());
        }
    }
    
    /**
     * Add a comment to an issue or PR.
     */
    public void addComment(String owner, String repo, int number, String comment) {
        if (!available) {
            throw new GitHubException("GitHub not configured");
        }
        
        try {
            String url = GITHUB_API_URL + "/repos/" + owner + "/" + repo + 
                    "/issues/" + number + "/comments";
            
            Map<String, Object> request = Map.of("body", comment);
            
            restTemplate.exchange(url, HttpMethod.POST, createAuthEntity(request), Map.class);
            
            logger.info("Added comment to {}/{}#{}", owner, repo, number);
            
        } catch (Exception e) {
            logger.error("Failed to add comment to {}/{}#{}", owner, repo, number, e);
            throw new GitHubException("Failed to add comment: " + e.getMessage());
        }
    }
    
    /**
     * List branches for a repository.
     */
    public List<GitHubBranch> listBranches(String owner, String repo, int limit) {
        if (!available) {
            throw new GitHubException("GitHub not configured");
        }
        
        try {
            String url = GITHUB_API_URL + "/repos/" + owner + "/" + repo + 
                    "/branches?per_page=" + limit;
            
            ResponseEntity<List> response = restTemplate.exchange(
                    url, HttpMethod.GET, createAuthEntity(null), List.class);
            
            List<Map<String, Object>> branches = response.getBody();
            if (branches == null) return Collections.emptyList();
            
            return branches.stream()
                    .map(b -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> commit = (Map<String, Object>) b.get("commit");
                        return new GitHubBranch(
                                (String) b.get("name"),
                                (String) commit.get("sha"),
                                (Boolean) b.get("protected")
                        );
                    })
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            logger.error("Failed to list branches for {}/{}", owner, repo, e);
            throw new GitHubException("Failed to list branches: " + e.getMessage());
        }
    }
    
    /**
     * List recent commits for a repository.
     */
    public List<GitHubCommit> listCommits(String owner, String repo, String branch, int limit) {
        if (!available) {
            throw new GitHubException("GitHub not configured");
        }
        
        try {
            String url = GITHUB_API_URL + "/repos/" + owner + "/" + repo + 
                    "/commits?per_page=" + limit;
            if (branch != null && !branch.isEmpty()) {
                url += "&sha=" + branch;
            }
            
            ResponseEntity<List> response = restTemplate.exchange(
                    url, HttpMethod.GET, createAuthEntity(null), List.class);
            
            List<Map<String, Object>> commits = response.getBody();
            if (commits == null) return Collections.emptyList();
            
            return commits.stream()
                    .map(this::parseCommit)
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            logger.error("Failed to list commits for {}/{}", owner, repo, e);
            throw new GitHubException("Failed to list commits: " + e.getMessage());
        }
    }
    
    /**
     * Close an issue.
     */
    public void closeIssue(String owner, String repo, int number) {
        if (!available) {
            throw new GitHubException("GitHub not configured");
        }
        
        try {
            String url = GITHUB_API_URL + "/repos/" + owner + "/" + repo + "/issues/" + number;
            
            Map<String, Object> request = Map.of("state", "closed");
            
            restTemplate.exchange(url, HttpMethod.PATCH, createAuthEntity(request), Map.class);
            
            logger.info("Closed issue {}/{}#{}", owner, repo, number);
            
        } catch (Exception e) {
            logger.error("Failed to close issue {}/{}#{}", owner, repo, number, e);
            throw new GitHubException("Failed to close issue: " + e.getMessage());
        }
    }
    
    /**
     * Get files changed in a PR with conflict status.
     */
    public List<GitHubPRFile> getPullRequestFiles(String owner, String repo, int number) {
        if (!available) {
            throw new GitHubException("GitHub not configured");
        }
        
        try {
            String url = GITHUB_API_URL + "/repos/" + owner + "/" + repo + "/pulls/" + number + "/files";
            
            ResponseEntity<List> response = restTemplate.exchange(
                    url, HttpMethod.GET, createAuthEntity(null), List.class);
            
            List<Map<String, Object>> files = response.getBody();
            if (files == null) return Collections.emptyList();
            
            return files.stream()
                    .map(f -> new GitHubPRFile(
                            (String) f.get("filename"),
                            (String) f.get("status"),
                            ((Number) f.getOrDefault("additions", 0)).intValue(),
                            ((Number) f.getOrDefault("deletions", 0)).intValue(),
                            ((Number) f.getOrDefault("changes", 0)).intValue()
                    ))
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            logger.error("Failed to get PR files {}/{}#{}", owner, repo, number, e);
            throw new GitHubException("Failed to get PR files: " + e.getMessage());
        }
    }
    
    /**
     * Get detailed merge status for a PR.
     */
    public MergeStatus getMergeStatus(String owner, String repo, int number) {
        if (!available) {
            throw new GitHubException("GitHub not configured");
        }
        
        GitHubPR pr = getPullRequest(owner, repo, number);
        List<GitHubPRFile> files = getPullRequestFiles(owner, repo, number);
        
        boolean canMerge = Boolean.TRUE.equals(pr.mergeable());
        boolean hasConflicts = "dirty".equals(pr.mergeableState()) || Boolean.FALSE.equals(pr.mergeable());
        
        String status;
        if (pr.merged()) {
            status = "already_merged";
        } else if (pr.state().equals("closed")) {
            status = "closed";
        } else if (hasConflicts) {
            status = "has_conflicts";
        } else if (canMerge) {
            status = "clean";
        } else if (pr.mergeable() == null) {
            status = "checking";
        } else {
            status = pr.mergeableState() != null ? pr.mergeableState() : "unknown";
        }
        
        return new MergeStatus(
                canMerge,
                hasConflicts,
                status,
                pr.mergeableState(),
                files,
                pr.headBranch(),
                pr.baseBranch()
        );
    }
    
    /**
     * Merge a pull request (with conflict checking).
     */
    public MergeResult mergePullRequest(String owner, String repo, int number, String commitTitle) {
        if (!available) {
            throw new GitHubException("GitHub not configured");
        }
        
        // First check merge status
        MergeStatus status = getMergeStatus(owner, repo, number);
        
        if (status.status().equals("already_merged")) {
            return new MergeResult(false, "PR is already merged", status);
        }
        
        if (status.status().equals("closed")) {
            return new MergeResult(false, "PR is closed", status);
        }
        
        if (status.hasConflicts()) {
            return new MergeResult(false, "PR has merge conflicts that must be resolved locally", status);
        }
        
        if (!status.canMerge()) {
            if (status.status().equals("checking")) {
                return new MergeResult(false, "GitHub is still checking mergeability. Please try again in a moment.", status);
            }
            return new MergeResult(false, "PR cannot be merged: " + status.mergeableState(), status);
        }
        
        try {
            String url = GITHUB_API_URL + "/repos/" + owner + "/" + repo + "/pulls/" + number + "/merge";
            
            Map<String, Object> request = new HashMap<>();
            if (commitTitle != null && !commitTitle.isEmpty()) {
                request.put("commit_title", commitTitle);
            }
            request.put("merge_method", "squash");
            
            restTemplate.exchange(url, HttpMethod.PUT, createAuthEntity(request), Map.class);
            
            logger.info("Merged PR {}/{}#{}", owner, repo, number);
            return new MergeResult(true, "PR merged successfully", status);
            
        } catch (Exception e) {
            logger.error("Failed to merge PR {}/{}#{}", owner, repo, number, e);
            return new MergeResult(false, "Failed to merge: " + e.getMessage(), status);
        }
    }
    
    // Helper methods
    
    private HttpEntity<Object> createAuthEntity(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("Authorization", "Bearer " + token);
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        
        return new HttpEntity<>(body, headers);
    }
    
    @SuppressWarnings("unchecked")
    private GitHubRepo parseRepo(Map<String, Object> data) {
        Map<String, Object> owner = (Map<String, Object>) data.get("owner");
        return new GitHubRepo(
                (String) data.get("name"),
                (String) data.get("full_name"),
                (String) owner.get("login"),
                (String) data.get("description"),
                (String) data.get("html_url"),
                (Boolean) data.get("private"),
                (Boolean) data.get("fork"),
                (String) data.get("language"),
                ((Number) data.getOrDefault("stargazers_count", 0)).intValue(),
                ((Number) data.getOrDefault("forks_count", 0)).intValue(),
                ((Number) data.getOrDefault("open_issues_count", 0)).intValue(),
                (String) data.get("default_branch"),
                (String) data.get("updated_at")
        );
    }
    
    @SuppressWarnings("unchecked")
    private GitHubPR parsePR(Map<String, Object> data) {
        Map<String, Object> user = (Map<String, Object>) data.get("user");
        Map<String, Object> head = (Map<String, Object>) data.get("head");
        Map<String, Object> base = (Map<String, Object>) data.get("base");
        
        // Mergeable info (may be null if not yet computed by GitHub)
        Boolean mergeable = (Boolean) data.get("mergeable");
        String mergeableState = (String) data.get("mergeable_state");
        
        return new GitHubPR(
                ((Number) data.get("number")).intValue(),
                (String) data.get("title"),
                (String) data.get("state"),
                (String) user.get("login"),
                (String) head.get("ref"),
                (String) base.get("ref"),
                (String) data.get("body"),
                (String) data.get("html_url"),
                (Boolean) data.get("merged"),
                (Boolean) data.get("draft"),
                mergeable,
                mergeableState,
                (String) data.get("created_at"),
                (String) data.get("updated_at")
        );
    }
    
    @SuppressWarnings("unchecked")
    private GitHubIssue parseIssue(Map<String, Object> data) {
        Map<String, Object> user = (Map<String, Object>) data.get("user");
        
        List<String> labels = new ArrayList<>();
        List<Map<String, Object>> labelList = (List<Map<String, Object>>) data.get("labels");
        if (labelList != null) {
            labels = labelList.stream()
                    .map(l -> (String) l.get("name"))
                    .collect(Collectors.toList());
        }
        
        return new GitHubIssue(
                ((Number) data.get("number")).intValue(),
                (String) data.get("title"),
                (String) data.get("state"),
                (String) user.get("login"),
                (String) data.get("body"),
                (String) data.get("html_url"),
                labels,
                (String) data.get("created_at"),
                (String) data.get("updated_at")
        );
    }
    
    @SuppressWarnings("unchecked")
    private GitHubCommit parseCommit(Map<String, Object> data) {
        Map<String, Object> commitData = (Map<String, Object>) data.get("commit");
        Map<String, Object> author = (Map<String, Object>) commitData.get("author");
        Map<String, Object> authorUser = (Map<String, Object>) data.get("author");
        
        return new GitHubCommit(
                (String) data.get("sha"),
                (String) commitData.get("message"),
                (String) author.get("name"),
                authorUser != null ? (String) authorUser.get("login") : null,
                (String) author.get("date"),
                (String) data.get("html_url")
        );
    }
    
    // DTO Classes
    
    public static record GitHubUser(
            String login,
            String name,
            String email,
            String avatarUrl,
            int publicRepos
    ) {}
    
    public static record GitHubRepo(
            String name,
            String fullName,
            String owner,
            String description,
            String htmlUrl,
            boolean isPrivate,
            boolean isFork,
            String language,
            int stars,
            int forks,
            int openIssues,
            String defaultBranch,
            String updatedAt
    ) {}
    
    public static record GitHubPR(
            int number,
            String title,
            String state,
            String author,
            String headBranch,
            String baseBranch,
            String body,
            String htmlUrl,
            boolean merged,
            boolean draft,
            Boolean mergeable,
            String mergeableState,
            String createdAt,
            String updatedAt
    ) {}
    
    public static record GitHubIssue(
            int number,
            String title,
            String state,
            String author,
            String body,
            String htmlUrl,
            List<String> labels,
            String createdAt,
            String updatedAt
    ) {}
    
    public static record GitHubBranch(
            String name,
            String sha,
            boolean isProtected
    ) {}
    
    public static record GitHubCommit(
            String sha,
            String message,
            String authorName,
            String authorLogin,
            String date,
            String htmlUrl
    ) {}
    
    public static record GitHubPRFile(
            String filename,
            String status,
            int additions,
            int deletions,
            int changes
    ) {}
    
    public static record MergeStatus(
            boolean canMerge,
            boolean hasConflicts,
            String status,
            String mergeableState,
            List<GitHubPRFile> files,
            String headBranch,
            String baseBranch
    ) {}
    
    public static record MergeResult(
            boolean success,
            String message,
            MergeStatus status
    ) {}
}

