# SDLCraft-CLI

**Agentic AI-Powered SDLC Automation Platform**

An autonomous agent system that helps you manage your Software Development Life Cycle - using AI-powered code analysis, Jira integration, and iterative workflows.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           SDLCraft Architecture                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐             │
│   │   JIRA   │    │  CODE    │    │    AI    │    │  DEPLOY  │             │
│   │ Tickets  │◄──▶│ Analysis │◄──▶│ Assistant│◄──▶│ Pipeline │             │
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

### Jira Integration

Manage Jira tickets from the command line:

```bash
# Check connection
sdlc jira status

# List projects
sdlc jira projects

# List issues
sdlc jira issues PROJ
sdlc jira issues PROJ -s "In Progress"  # Filter by status
sdlc jira issues PROJ -l 50              # Limit results

# Get issue details
sdlc jira issue PROJ-123

# Create an issue
sdlc jira create PROJ "Fix login bug" -t Bug -d "Users can't login"

# Transition issue status
sdlc jira transition PROJ-123 "In Progress"
sdlc jira transition PROJ-123 "Done"

# Add a comment
sdlc jira comment PROJ-123 "Fixed in commit abc123"
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
JIRA_TOKEN=your-api-token

# Get Jira API token at: https://id.atlassian.com/manage/api-tokens
```

### application.yml

Backend configuration in `backend/src/main/resources/application.yml`:

```yaml
sdlcraft:
  llm:
    ollama:
      base-url: http://localhost:11434
      model: llama3.2
  
  integrations:
    jira:
      url: ${JIRA_URL:}
      email: ${JIRA_EMAIL:}
      token: ${JIRA_TOKEN:}
```

## 📁 Project Structure

```
SDLCraft-CLI/
├── cli/                      # Go CLI application
│   ├── cmd/
│   │   ├── root.go          # Root command
│   │   ├── ai.go            # AI code analysis
│   │   └── jira.go          # Jira commands
│   └── client/              # HTTP client for backend
│
├── backend/                  # Java Spring Boot backend
│   └── src/main/java/com/sdlcraft/backend/
│       ├── llm/             # LLM providers (Ollama, Anthropic)
│       ├── rag/             # RAG service for code analysis
│       ├── memory/          # Codebase indexing
│       └── integration/     # External integrations
│           └── jira/        # Jira service
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

### 1. Code Understanding

```bash
# Understand how something works
sdlc ai "how does the payment processing work?"

# Find specific functionality
sdlc ai "where is user authentication implemented?"

# Get suggestions
sdlc ai "how can I improve error handling in this project?"
```

### 2. Code Modifications

```bash
# Add new functionality
sdlc ai --apply "add input validation to the user registration"

# Fix issues
sdlc ai --apply "fix the null pointer exception in UserService"

# Refactor code
sdlc ai --dry-run "refactor duplicate code in the API handlers"
```

### 3. SDLC Workflow

```bash
# Create issue for a bug you found
sdlc jira create PROJ "Bug: Login fails with special characters" -t Bug

# Start working on it
sdlc jira transition PROJ-1 "In Progress"

# Analyze and fix
sdlc ai -p ./src "find and fix the login bug with special characters"

# Mark as done
sdlc jira comment PROJ-1 "Fixed - special characters are now escaped"
sdlc jira transition PROJ-1 "Done"
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

## 📝 License

MIT

---

**Made with ❤️ for developers who want AI-powered SDLC automation**
