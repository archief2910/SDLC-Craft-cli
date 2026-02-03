# SDLCraft CLI

**Intent-aware, self-healing SDLC orchestration engine**

SDLCraft CLI is a production-grade command-line tool that acts as a compiler for developer intent. It understands what you want to accomplish even when commands are imperfect, and executes complex SDLC workflows through autonomous agents.

## Overview

The system combines deterministic command repair with AI-based intent inference to provide a senior engineer experience inside the terminal. Instead of memorizing exact syntax, developers express their goals naturally, and SDLCraft figures out what to do.

### Key Features

- **Self-Healing Commands**: Never fails on typos or imperfect syntax - always attempts repair
- **Intent-First Interaction**: Express goals, not syntax (`sdlc improve performance` instead of memorizing flags)
- **Agentic Execution**: Complex workflows delegated to autonomous agents following PLAN → ACT → OBSERVE → REFLECT
- **SDLC State Tracking**: Understands your project's current phase, risks, and readiness
- **Long-Term Memory**: Learns from past executions to provide better recommendations
- **Safety First**: Requires confirmation for high-risk operations with clear impact explanations

## Architecture

SDLCraft consists of two primary components:

### CLI (Go + Cobra)
- **Local-first design**: Deterministic operations happen without network calls
- **Command Parser**: Extracts intent, target, and modifiers from user input
- **Repair Engine**: Corrects typos, normalizes flags, fixes ordering
- **Output Renderer**: Streams execution progress with color-coded output

### Backend (Spring Boot + Java 17)
- **Intent Inference Service**: Maps natural language to structured intents using LLM abstraction
- **Agent Orchestrator**: Coordinates multi-agent workflows (Planner, Executor, Validator, Reflection)
- **SDLC State Machine**: Tracks project phase, risk level, coverage, and release readiness
- **Policy Engine**: Enforces safety rules and confirmation requirements
- **Long-Term Memory**: Stores context in PostgreSQL and vector store for semantic retrieval

```
┌─────────────┐
│  Developer  │
└──────┬──────┘
       │
       ▼
┌─────────────────────────────────────┐
│  CLI (Go)                           │
│  • Command Parser                   │
│  • Repair Engine (Deterministic)    │
│  • Output Renderer                  │
└──────┬──────────────────────────────┘
       │ JSON over HTTP/IPC
       ▼
┌─────────────────────────────────────┐
│  Backend (Spring Boot)              │
│  • Intent Inference Service         │
│  • Agent Orchestrator               │
│  • SDLC State Machine               │
│  • Policy Engine                    │
└──────┬──────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────┐
│  Persistence                        │
│  • PostgreSQL (State, Audit)        │
│  • Vector Store (Context, Memory)   │
└─────────────────────────────────────┘
```

## Project Structure

```
sdlcraft/
├── cli/                    # Go-based CLI component
│   ├── main.go            # Entry point with Cobra setup
│   ├── cmd/               # Command implementations
│   ├── parser/            # Command parsing logic
│   ├── repair/            # Deterministic repair engine
│   ├── client/            # Backend communication
│   └── renderer/          # Output rendering
│
├── backend/               # Spring Boot backend
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/sdlcraft/backend/
│   │   │   │   ├── intent/        # Intent inference service
│   │   │   │   ├── agent/         # Agent orchestrator and implementations
│   │   │   │   ├── state/         # SDLC state machine
│   │   │   │   ├── policy/        # Policy engine
│   │   │   │   ├── memory/        # Long-term memory
│   │   │   │   └── api/           # REST API controllers
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       └── db/migration/  # Flyway migrations
│   │   └── test/                  # Unit and property tests
│   └── pom.xml
│
└── docs/                  # Additional documentation
    ├── architecture.md    # Detailed architecture
    ├── api.md            # API documentation
    └── development.md    # Development guide
```

## Getting Started

### Prerequisites

- **Go 1.21+** for CLI development
- **Java 17+** for backend development
- **PostgreSQL 14+** for state storage
- **Maven 3.8+** for building backend

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/sdlcraft/sdlcraft-cli.git
   cd sdlcraft-cli
   ```

2. **Set up PostgreSQL**
   ```bash
   # Create database and user
   createdb sdlcraft
   psql sdlcraft -c "CREATE USER sdlcraft WITH PASSWORD 'sdlcraft';"
   psql sdlcraft -c "GRANT ALL PRIVILEGES ON DATABASE sdlcraft TO sdlcraft;"
   ```

3. **Build and run the backend**
   ```bash
   cd backend
   mvn clean install
   mvn spring-boot:run
   ```
   
   The backend will start on `http://localhost:8080` and automatically run Flyway migrations.

4. **Build and install the CLI**
   ```bash
   cd cli
   go mod download
   go build -o sdlc
   sudo mv sdlc /usr/local/bin/  # Or add to PATH
   ```

5. **Verify installation**
   ```bash
   sdlc --help
   ```

## Usage Examples

### Basic Commands

```bash
# Check current SDLC state
sdlc status

# Analyze security
sdlc analyze security

# Improve performance
sdlc improve performance

# Run tests
sdlc test unit

# Prepare for release
sdlc prepare release

# Release to production (requires confirmation)
sdlc release production
```

### Self-Healing Examples

```bash
# Typo correction
$ sdlc stauts
✓ Corrected to: sdlc status
Current Phase: DEVELOPMENT
Risk Level: MEDIUM
Test Coverage: 78.5%
...

# Natural language
$ sdlc show me the current status
✓ Interpreted as: sdlc status
...

# Multiple candidates
$ sdlc analy
? Did you mean:
  1. sdlc analyze
  2. sdlc analyze security
  3. sdlc analyze performance
Select option (1-3):
```

## Design Decisions

### Why Local-First CLI?

The CLI performs deterministic operations (typo correction, flag normalization) locally without network calls. This ensures:
- **Fast response times** for common operations (< 50ms)
- **Offline capability** for basic command repair
- **Reduced backend load** by handling simple cases locally

Only complex operations (intent inference, agent orchestration) require backend communication.

### Why PLAN → ACT → OBSERVE → REFLECT?

This agent pattern ensures:
- **Explainability**: Each step has clear reasoning
- **Error Recovery**: Reflection agent analyzes failures and suggests fixes
- **Learning**: Observations feed back into future planning
- **Modularity**: Each phase can be tested and improved independently

### Why PostgreSQL + Vector Store?

- **PostgreSQL**: Structured data (state, commands, audit logs) with ACID guarantees
- **Vector Store**: Semantic search over project context and historical decisions
- **Separation of Concerns**: Right tool for each data type

### Why Interface-Based LLM Abstraction?

The backend uses an `LLMProvider` interface with no hard dependencies on specific providers. This allows:
- **Flexibility**: Swap OpenAI, Anthropic, local models without code changes
- **Testing**: Mock LLM responses for deterministic tests
- **Cost Control**: Use different providers for different operations

## Development

### Running Tests

**CLI Tests (Go)**:
```bash
cd cli
go test ./... -v
```

**Backend Tests (Java)**:
```bash
cd backend
mvn test
```

**Property-Based Tests**:
```bash
# CLI (using gopter)
cd cli
go test -tags=property ./... -v

# Backend (using jqwik)
cd backend
mvn test -Dtest=**/*PropertyTest
```

### Code Quality

- **Strong typing** throughout both codebases
- **No placeholder logic** or TODO comments in production code
- **No hardcoded LLM responses** or mock data
- **Comments explain WHY**, not just WHAT
- **Comprehensive error handling** with structured responses

### Testing Strategy

The project uses **dual testing approach**:

1. **Unit Tests**: Verify specific examples, edge cases, error conditions
2. **Property Tests**: Verify universal properties across all inputs

Both are necessary and complementary. Unit tests catch concrete bugs, property tests verify general correctness.

**Property Test Configuration**:
- Minimum 100 iterations per property test
- Each property references its design document property
- Tag format: `Feature: sdlcraft-cli, Property {number}: {property_text}`

## Contributing

1. Read the [requirements document](.kiro/specs/sdlcraft-cli/requirements.md)
2. Review the [design document](.kiro/specs/sdlcraft-cli/design.md)
3. Check the [task list](.kiro/specs/sdlcraft-cli/tasks.md) for current work
4. Follow the incremental development path
5. Write both unit tests and property tests
6. Ensure all tests pass before submitting PR

## License

MIT License - See LICENSE file for details

## Architecture Diagrams

### Command Flow

**Simple Command (Local Repair)**:
```
User Input → Parser → Repair Engine → Execute → Display
```

**Complex Command (Backend Required)**:
```
User Input → Parser → Repair (fails) → Backend Intent Service
→ Agent Orchestrator → Agents (PLAN/ACT/OBSERVE/REFLECT)
→ State Update → Stream to CLI → Display
```

**High-Risk Command**:
```
User Input → Parser → Backend Policy Engine → Risk Assessment
→ CLI Confirmation → User Confirms → Agent Execution → Audit Log
```

### Agent Workflow

```
┌──────────────┐
│ PlannerAgent │ Creates execution plan from intent
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ExecutorAgent │ Executes planned actions
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ValidatorAgent│ Validates execution results
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ReflectionAgt │ Analyzes outcomes, suggests improvements
└──────────────┘
```

## Status

**Current Phase**: Initial Development  
**Version**: 0.1.0-SNAPSHOT

This is an active development project. The monorepo structure and scaffolding are complete. Next steps include implementing the command parser, repair engine, and core backend services.

See [tasks.md](.kiro/specs/sdlcraft-cli/tasks.md) for detailed implementation progress.
