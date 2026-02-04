# SDLCraft-CLI

**Agentic AI-Powered SDLC Automation Platform**

An autonomous agent system that orchestrates your entire Software Development Life Cycle - from Jira tickets to production deployments - using iterative, test-driven AI workflows.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           SDLCraft Architecture                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐             │
│   │   JIRA   │    │BITBUCKET │    │   AWS    │    │  DOCKER  │             │
│   │ Tickets  │───▶│   Code   │───▶│  Infra   │───▶│  Deploy  │             │
│   └────┬─────┘    └────┬─────┘    └────┬─────┘    └────┬─────┘             │
│        │               │               │               │                    │
│        └───────────────┴───────────────┴───────────────┘                    │
│                               │                                              │
│                    ┌──────────▼──────────┐                                  │
│                    │   AGENT ORCHESTRATOR │                                  │
│                    │  ┌────────────────┐ │                                  │
│                    │  │ PLAN → ACT →   │ │                                  │
│                    │  │ OBSERVE → REFLECT│ │                                  │
│                    │  └────────────────┘ │                                  │
│                    └──────────┬──────────┘                                  │
│                               │                                              │
│        ┌──────────────────────┼──────────────────────┐                      │
│        ▼                      ▼                      ▼                      │
│   ┌─────────┐           ┌─────────┐           ┌─────────┐                   │
│   │ Planner │           │Executor │           │Validator│                   │
│   │  Agent  │           │  Agent  │           │  Agent  │                   │
│   └─────────┘           └─────────┘           └─────────┘                   │
│                                                                              │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                         LLM LAYER                                    │   │
│   │   OpenRouter │ OpenAI │ Anthropic │ Ollama (Local)                  │   │
│   └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                       MEMORY & CONTEXT                               │   │
│   │   Pinecone Vector Store │ RAG │ Codebase Index │ Execution History  │   │
│   └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Core Workflow

SDLCraft follows the **micro-agent pattern**: iterative, test-driven execution until success.

```
┌─────────────────────────────────────────────────────────────────┐
│                    ITERATIVE AGENT LOOP                         │
│                                                                  │
│   ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐     │
│   │ RECEIVE │───▶│ GENERATE│───▶│  TEST   │───▶│ VERIFY  │     │
│   │  INTENT │    │  PLAN   │    │ EXECUTE │    │ RESULT  │     │
│   └─────────┘    └─────────┘    └─────────┘    └────┬────┘     │
│                                                      │          │
│                                    ┌─────────────────┤          │
│                                    │                 │          │
│                              ┌─────▼─────┐    ┌─────▼─────┐    │
│                              │  FAILED   │    │  SUCCESS  │    │
│                              │  Iterate  │    │  Complete │    │
│                              └─────┬─────┘    └───────────┘    │
│                                    │                            │
│                                    └────────────────────────────┤
│                                              ▲                  │
│                                              │                  │
│                              ┌───────────────┘                  │
│                              │  Max 20 iterations               │
│                              │  with error feedback             │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

## Integrations

### Jira Integration
```
sdlc jira sync                    # Sync project tickets
sdlc jira create "Bug: API 500"   # Create ticket via AI
sdlc jira transition PROJ-123     # Auto-transition based on code state
```

### Bitbucket Integration
```
sdlc bb pr create                 # Create PR with AI-generated description
sdlc bb review                    # AI-powered code review
sdlc bb merge --when-green        # Auto-merge when CI passes
```

### AWS Integration
```
sdlc aws deploy staging           # Deploy to staging environment
sdlc aws infra plan               # Generate Terraform plan
sdlc aws scale --auto             # AI-driven auto-scaling decisions
```

### Docker Integration
```
sdlc docker build                 # Build with optimized Dockerfile
sdlc docker push ecr              # Push to ECR
sdlc docker compose up            # Start local development
```

### QA Automation
```
sdlc qa run                       # Execute test suite
sdlc qa generate                  # AI-generate tests from code
sdlc qa coverage                  # Analyze and improve coverage
```

## Quick Start

```bash
# Configure integrations
sdlc config set jira.url https://your-org.atlassian.net
sdlc config set jira.token <your-token>
sdlc config set bitbucket.url https://bitbucket.org/your-org
sdlc config set aws.profile default
sdlc config set llm.provider openrouter
sdlc config set llm.key <your-api-key>

# Start automating
sdlc ai "pick up PROJ-123, implement the feature, create PR, deploy to staging"
```

## Agent Types

| Agent | Purpose | Phase |
|-------|---------|-------|
| **Planner** | Analyzes intent, creates execution plan | PLAN |
| **Executor** | Executes planned actions against integrations | ACT |
| **Validator** | Verifies outcomes, runs tests | OBSERVE |
| **Reflection** | Learns from failures, adjusts strategy | REFLECT |

## Tech Stack

| Component | Technology |
|-----------|------------|
| CLI | Go + Cobra |
| Backend | Java 17 + Spring Boot 3 |
| LLM | OpenRouter / OpenAI / Anthropic / Ollama |
| Vector Store | Pinecone |
| Database | PostgreSQL / H2 |
| Containerization | Docker |
| Cloud | AWS |

## Configuration

Environment variables or `~/.sdlcraft/config.yml`:

```yaml
llm:
  provider: openrouter
  model: anthropic/claude-3.5-sonnet
  api_key: ${OPENROUTER_API_KEY}

jira:
  url: https://your-org.atlassian.net
  email: you@company.com
  token: ${JIRA_API_TOKEN}

bitbucket:
  url: https://api.bitbucket.org/2.0
  username: your-username
  app_password: ${BITBUCKET_APP_PASSWORD}

aws:
  profile: default
  region: us-east-1

docker:
  registry: your-account.dkr.ecr.us-east-1.amazonaws.com

qa:
  test_command: npm test
  coverage_threshold: 80
```

## Example Workflows

### 1. Full Feature Development
```bash
sdlc ai "implement user authentication with JWT tokens"
```

The agent will:
1. **PLAN**: Analyze codebase, identify files to modify, plan implementation
2. **ACT**: Generate code, create/modify files
3. **OBSERVE**: Run tests, check for errors
4. **REFLECT**: If tests fail, analyze errors and retry (up to 20x)

### 2. Bug Fix from Jira
```bash
sdlc workflow bug-fix PROJ-456
```

Flow:
```
Jira Ticket → Analyze Bug → Generate Fix → Test → Create PR → Request Review
```

### 3. Release Pipeline
```bash
sdlc workflow release v1.2.0
```

Flow:
```
Version Bump → Changelog → Build → Test → Docker Push → Deploy Staging → Smoke Tests → Deploy Production
```

## Project Structure

```
SDLCraft-CLI/
├── cli/                      # Go CLI application
│   ├── cmd/                  # Command implementations
│   │   ├── root.go
│   │   ├── ai.go            # Natural language commands
│   │   ├── jira.go          # Jira integration commands
│   │   ├── bitbucket.go     # Bitbucket commands
│   │   ├── aws.go           # AWS commands
│   │   ├── docker.go        # Docker commands
│   │   └── qa.go            # QA automation commands
│   ├── client/              # HTTP client for backend
│   ├── parser/              # Intent parsing & repair
│   └── renderer/            # Terminal UI rendering
│
├── backend/                  # Java Spring Boot backend
│   └── src/main/java/com/sdlcraft/backend/
│       ├── agent/           # Multi-agent orchestration
│       │   ├── PlannerAgent.java
│       │   ├── ExecutorAgent.java
│       │   ├── ValidatorAgent.java
│       │   └── ReflectionAgent.java
│       ├── integration/     # External service integrations
│       │   ├── jira/
│       │   ├── bitbucket/
│       │   ├── aws/
│       │   ├── docker/
│       │   └── qa/
│       ├── llm/             # LLM providers
│       ├── memory/          # Vector store & RAG
│       └── workflow/        # Workflow definitions
│
└── config/                   # Configuration templates
```

## Development

```bash
# Backend
cd backend && ./mvnw spring-boot:run

# CLI
cd cli && go build -o sdlc && ./sdlc --help
```

## License

MIT

