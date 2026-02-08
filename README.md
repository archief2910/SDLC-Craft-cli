# SDLCraft-CLI

**Agentic AI-Powered SDLC Automation Platform**

An autonomous agent system that helps you manage your Software Development Life Cycle - using AI-powered code analysis, Jira & GitHub integration, and iterative workflows.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           SDLCraft Architecture                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐             │
│   │   JIRA   │    │  GITHUB  │    │    AI    │    │  DEPLOY  │             │
│   │ Tickets  │◄──▶│ PRs/Code │◄──▶│ Assistant│◄──▶│ Pipeline │             │
│   └──────────┘    └──────────┘    └──────────┘    └──────────┘             │
│                                                                              │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                         LLM LAYER                                    │   │
│   │              Ollama (Local) │ Anthropic │ OpenAI                    │   │
│   └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                       MEMORY & CONTEXT                               │   │
│   │          Codebase Indexer │ RAG │ File Tree │ Execution History     │   │
│   └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## ✨ Features

- 🤖 **AI Code Analysis** - Ask questions about any codebase in natural language
- 🎫 **Jira Integration** - Create, list, transition, and comment on issues from CLI
- 🐙 **GitHub Integration** - Manage repos, PRs, issues, branches, and commits
- 🔄 **Iterative Workflows** - Test-driven AI that retries until success
- 🏠 **Local LLM Support** - Works offline with Ollama (no API keys needed!)

## 🚀 Quick Start

### Prerequisites

1. **Java 17+** (JDK 21 or 25 recommended)
2. **Go 1.21+** (for CLI)
3. **Ollama** (for local AI) - [Download](https://ollama.ai)

### Installation

```bash
# 1. Clone the repository
git clone https://github.com/your-org/SDLCraft-CLI.git
cd SDLCraft-CLI

# 2. Install and start Ollama
ollama pull llama3.2
ollama serve  # Keep running in background

# 3. Start the backend
cd backend
.\run.ps1    # Windows
./run.sh     # Linux/Mac

# 4. Build the CLI
cd ../cli
go build -o sdlc.exe   # Windows
go build -o sdlc       # Linux/Mac
```

## 📖 Usage

### AI Code Analysis

Analyze any codebase with natural language:

```bash
# Analyze current directory
sdlc ai "explain how the authentication works"

# Analyze a specific project
sdlc ai -p /path/to/project "find all API endpoints"

# Apply suggested changes
sdlc ai --apply "add error handling to the main function"

# Interactive mode - review each change
sdlc ai -i "refactor for better performance"
```

### 🎫 Jira Integration

Manage Jira tickets from the command line:

```bash
# Check connection
sdlc jira status

# List projects
sdlc jira projects

# List issues
sdlc jira issues PROJ
sdlc jira issues PROJ -s "In Progress"  # Filter by status

# Get issue details
sdlc jira issue PROJ-123

# Create an issue
sdlc jira create PROJ "Fix login bug" -t Bug -d "Description"

# Transition issue status
sdlc jira transition PROJ-123 "In Progress"

# Add a comment
sdlc jira comment PROJ-123 "Fixed in commit abc123"
```

### 🐙 GitHub Integration

Manage GitHub repositories, PRs, and issues:

```bash
# Check connection
sdlc gh status

# List your repositories
sdlc gh repos
sdlc gh repos -t private -l 50  # Filter by type, limit

# Get repo details
sdlc gh repo owner/repo

# List and manage pull requests
sdlc gh prs owner/repo
sdlc gh prs owner/repo -s closed  # Show closed PRs
sdlc gh pr owner/repo 123         # Get PR details (shows merge status)
sdlc gh pr-create owner/repo "Add feature" -H feature-branch -B main

# Check merge conflicts before merging
sdlc gh pr-status owner/repo 123  # Shows conflicts & files changed
sdlc gh pr-merge owner/repo 123   # Merges (or shows conflict resolution steps)

# List and manage issues
sdlc gh issues owner/repo
sdlc gh issues owner/repo -s all  # Show all issues
sdlc gh issue-create owner/repo "Bug: Login fails" -l bug,urgent
sdlc gh issue-close owner/repo 45

# Add comments
sdlc gh comment owner/repo 123 "This is fixed now"

# View branches and commits
sdlc gh branches owner/repo
sdlc gh commits owner/repo -b main -l 20
```

#### Merge Conflict Detection

SDLCraft checks for merge conflicts before merging:

```bash
# Check if a PR can be merged
sdlc gh pr-status owner/repo 123

# Output:
# ═══════════════════════════════════════════════════════════════
# 🔀 Merge Status for PR #123
# ═══════════════════════════════════════════════════════════════
#
# 🌿 feature-branch → main
#
# ❌ Status: Has merge conflicts
#
# 📄 Files changed (3):
#    ✏️ src/main.go (+45/-12)
#    ➕ src/utils.go (+30/-0)
#    ✏️ config.yml (+5/-2)
#
# 🔧 To resolve conflicts locally:
#    git checkout main
#    git pull
#    git merge origin/feature-branch
#    # Resolve conflicts in your editor
#    git add .
#    git commit
#    git push
```

## ⚙️ Configuration

### Environment Variables

Create a `.env` file in the project root:

```env
# LLM Provider (Ollama is default - no API key needed!)
# ANTHROPIC_API_KEY=sk-ant-...  # Optional: for Anthropic Claude

# Jira Integration (optional)
JIRA_URL=https://your-org.atlassian.net
JIRA_EMAIL=your-email@company.com
JIRA_TOKEN=your-jira-api-token

# GitHub Integration (optional)
GITHUB_TOKEN=ghp_your_github_token
GITHUB_USERNAME=your-github-username  # Optional
```

### Getting API Tokens

**Jira Token:**
1. Go to: https://id.atlassian.com/manage/api-tokens
2. Click "Create API token"
3. Copy and add to `.env`

**GitHub Token:**
1. Go to: https://github.com/settings/tokens
2. Generate new token (classic)
3. Select scopes: `repo`, `read:user`
4. Copy and add to `.env`

## 📁 Project Structure

```
SDLCraft-CLI/
├── cli/                      # Go CLI application
│   ├── cmd/
│   │   ├── root.go          # Root command
│   │   ├── ai.go            # AI code analysis
│   │   ├── jira.go          # Jira commands
│   │   └── github.go        # GitHub commands
│   └── client/              # HTTP client for backend
│
├── backend/                  # Java Spring Boot backend
│   └── src/main/java/com/sdlcraft/backend/
│       ├── llm/             # LLM providers (Ollama, Anthropic)
│       ├── rag/             # RAG service for code analysis
│       ├── memory/          # Codebase indexing
│       └── integration/     # External integrations
│           ├── jira/        # Jira service
│           └── github/      # GitHub service
│
├── .env                      # Environment variables
└── README.md
```

## 🔧 Tech Stack

| Component | Technology |
|-----------|------------|
| CLI | Go + Cobra |
| Backend | Java 21 + Spring Boot 3 |
| LLM | Ollama (local) / Anthropic |
| Database | H2 (embedded) |
| Code Indexing | File-based (no external vector DB) |

## 💡 Examples

### 1. Complete SDLC Workflow

```bash
# 1. Create a Jira issue for your task
sdlc jira create PROJ "Implement user authentication" -t Story

# 2. Start working on it
sdlc jira transition PROJ-42 "In Progress"

# 3. Analyze existing code
sdlc ai -p ./src "how is authentication currently handled?"

# 4. Get AI suggestions
sdlc ai --apply "add JWT authentication to the login endpoint"

# 5. Create a GitHub PR
sdlc gh pr-create myorg/myrepo "Add JWT authentication" -H feature/jwt-auth -B main

# 6. Link and update Jira
sdlc jira comment PROJ-42 "PR created: https://github.com/myorg/myrepo/pull/123"

# 7. After review, merge the PR
sdlc gh pr-merge myorg/myrepo 123

# 8. Close the Jira issue
sdlc jira transition PROJ-42 "Done"
```

### 2. Code Review Workflow

```bash
# View open PRs
sdlc gh prs myorg/myrepo

# Check PR details
sdlc gh pr myorg/myrepo 45

# Analyze the codebase for context
sdlc ai -p ./myrepo "explain the changes in the auth module"

# Leave a comment
sdlc gh comment myorg/myrepo 45 "LGTM! Great refactoring."

# Merge when ready
sdlc gh pr-merge myorg/myrepo 45
```

### 3. Bug Fixing Workflow

```bash
# Find the bug in code
sdlc ai "why does the login fail with special characters?"

# Create a GitHub issue
sdlc gh issue-create myorg/myrepo "Bug: Login fails with special chars" -l bug

# Fix with AI assistance
sdlc ai --apply "escape special characters in the login form"

# Close the issue
sdlc gh issue-close myorg/myrepo 67
```

## 🛠️ Development

```bash
# Run backend in development
cd backend
mvn spring-boot:run

# Build CLI
cd cli
go build -o sdlc

# Run tests
cd backend && mvn test
cd cli && go test ./...
```

## 🗺️ Roadmap

- [x] AI-powered code analysis (RAG)
- [x] Jira integration
- [x] GitHub integration
- [ ] Docker integration
- [ ] AWS deployment integration
- [ ] CI/CD pipeline integration
- [ ] Slack/Teams notifications

## 📝 License

MIT

---

**Made with ❤️ for developers who want AI-powered SDLC automation**
