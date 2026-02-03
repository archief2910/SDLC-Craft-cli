# SDLCraft CLI - Architecture Documentation

## Table of Contents

1. [System Overview](#system-overview)
2. [Component Architecture](#component-architecture)
3. [Data Flow](#data-flow)
4. [Design Principles](#design-principles)
5. [Technology Stack](#technology-stack)
6. [Scalability Considerations](#scalability-considerations)

## System Overview

SDLCraft CLI is designed as a **local-first, intent-aware orchestration engine** that acts as a compiler for developer intent. The architecture separates concerns between fast local operations (CLI) and complex AI-powered operations (Backend).

### Core Design Philosophy

1. **Local-First**: Deterministic operations happen in the CLI without network calls
2. **Intent-Driven**: Developers express goals, not memorize syntax
3. **Self-Healing**: Never fail on imperfect input - always attempt repair
4. **Agentic**: Complex workflows delegated to autonomous agents
5. **Explainable**: Every decision includes traceable reasoning
6. **Safe**: High-risk operations require explicit confirmation

## Component Architecture

### CLI Component (Go)

The CLI is responsible for user interaction, local command processing, and communication with the backend.

#### Command Parser

**Purpose**: Transform raw user input into structured Command objects.

**Key Responsibilities**:
- Parse `sdlc <intent> <target> [modifiers]` grammar
- Detect natural language vs. structured input
- Extract intent, target, and modifiers
- Validate grammar patterns

**Design Decision**: Uses regex-based parsing for speed and simplicity. More complex parsing (natural language) is delegated to the backend.

#### Command Repair Engine

**Purpose**: Deterministically repair invalid commands without backend calls.

**Key Responsibilities**:
- Typo correction using Levenshtein distance (threshold ≤ 2)
- Flag normalization (`--flag`, `-f`, `flag` → standard form)
- Argument ordering fixes
- Synonym expansion (`check` → `status`)

**Design Decision**: Operates entirely locally using edit distance algorithms. Only invokes backend when deterministic repair fails (confidence < 0.5).

**Repair Confidence Levels**:
- **> 0.9**: Auto-correct and display corrected command
- **0.5-0.9**: Present multiple candidates for user selection
- **< 0.5**: Invoke backend Intent Inference Service

#### Backend Client

**Purpose**: Communicate with Spring Boot backend for complex operations.

**Key Responsibilities**:
- Send intent inference requests
- Stream agent execution progress
- Query SDLC state
- Handle backend unavailability gracefully

**Design Decision**: Supports both HTTP and Unix socket communication. Uses Server-Sent Events (SSE) for streaming to avoid WebSocket complexity.

**Communication Protocol**:
```json
// Request
{
  "command": {
    "raw": "sdlc improve performance",
    "intent": "improve",
    "target": "performance",
    "modifiers": {}
  },
  "context": {
    "projectPath": "/path/to/project",
    "userId": "user123"
  }
}

// Response
{
  "intent": "improve",
  "target": "performance",
  "plan": ["Analyze bottlenecks", "Generate suggestions"],
  "requiresConfirmation": false,
  "riskLevel": "MEDIUM",
  "explanation": "Will analyze performance metrics and suggest optimizations"
}
```

#### Output Renderer

**Purpose**: Display results, progress, and prompts to the user.

**Key Responsibilities**:
- Color-coded output (green=success, yellow=warning, red=error)
- Progress indicators for long-running operations
- Confirmation prompts with risk indicators
- Verbose mode for agent reasoning

**Design Decision**: Uses ANSI color codes for terminal output. Supports both interactive and non-interactive modes (for CI/CD).

### Backend Component (Spring Boot)

The backend handles complex AI-powered operations, state management, and agent orchestration.

#### Intent Inference Service

**Purpose**: Convert natural language and ambiguous commands into structured intents.

**Key Responsibilities**:
- Map natural language to structured intent
- Query long-term memory for similar past commands
- Apply intent templates for common patterns
- Return clarification questions if confidence < 0.7

**Design Decision**: Uses LLM abstraction layer (`LLMProvider` interface) with no hard dependencies. This allows swapping providers (OpenAI, Anthropic, local models) without code changes.

**LLM Abstraction**:
```java
public interface LLMProvider {
    String complete(String prompt, Map<String, Object> parameters);
    List<String> embed(List<String> texts);
}
```

**Intent Inference Flow**:
```
Natural Language Input
    ↓
Query Long-Term Memory (similar past commands)
    ↓
Apply Intent Templates (common patterns)
    ↓
LLM Inference (if needed)
    ↓
Structured Intent + Confidence + Explanation
```

#### Agent Orchestrator

**Purpose**: Coordinate multi-agent workflows following PLAN → ACT → OBSERVE → REFLECT pattern.

**Key Responsibilities**:
- Create execution plans from intents
- Coordinate multiple agents
- Maintain execution context across transitions
- Handle timeouts and cancellations

**Design Decision**: Each agent implements a common `Agent` interface with four methods: `plan()`, `act()`, `observe()`, `reflect()`. This ensures consistency and testability.

**Agent Types**:

1. **PlannerAgent**: Creates execution plans
   - Breaks down high-level intent into concrete steps
   - Identifies required resources and dependencies
   - Estimates execution time and risk

2. **ExecutorAgent**: Executes planned actions
   - Runs commands, scripts, or API calls
   - Handles retries and error recovery
   - Reports progress to orchestrator

3. **ValidatorAgent**: Validates execution results
   - Checks output against expected criteria
   - Verifies state changes
   - Identifies anomalies or failures

4. **ReflectionAgent**: Analyzes execution and suggests improvements
   - Compares actual vs. expected outcomes
   - Identifies failure root causes
   - Suggests recovery actions or plan adjustments

**Agent Workflow**:
```
Intent → PlannerAgent.plan()
    ↓
ExecutorAgent.act(plan)
    ↓
ValidatorAgent.observe(result)
    ↓
ReflectionAgent.reflect(observation)
    ↓
Update State + Return Results
```

#### SDLC State Machine

**Purpose**: Track and manage project SDLC state.

**Key Responsibilities**:
- Track project phase (PLANNING, DEVELOPMENT, TESTING, STAGING, PRODUCTION)
- Maintain risk assessments
- Track test coverage metrics
- Calculate release readiness scores

**Design Decision**: State changes are persisted to PostgreSQL with timestamps. Risk scores are calculated using a weighted formula considering multiple factors.

**Risk Calculation Formula**:
```
Risk Score = (
    (1 - testCoverage) * 0.4 +
    (openIssues / totalIssues) * 0.3 +
    (daysSinceLastDeploy / 30) * 0.2 +
    customRiskFactors * 0.1
)
```

**State Transitions**:
```
PLANNING → DEVELOPMENT → TESTING → STAGING → PRODUCTION
    ↑                                              ↓
    └──────────────────────────────────────────────┘
                    (rollback)
```

#### Policy Engine

**Purpose**: Enforce safety policies and determine confirmation requirements.

**Key Responsibilities**:
- Classify commands by risk level (LOW, MEDIUM, HIGH, CRITICAL)
- Determine confirmation requirements
- Check policy violations
- Log all policy decisions

**Design Decision**: Policies are defined as rules that can be registered at runtime. This allows customization without code changes.

**Risk Classification Rules**:
- **HIGH**: Operations on production, delete operations, reset operations
- **MEDIUM**: Operations on staging, bulk updates, schema changes
- **LOW**: Read operations, analysis, status checks

**Policy Check Flow**:
```
Intent → Policy Engine
    ↓
Risk Assessment (based on intent + current state)
    ↓
Requires Confirmation? (HIGH or CRITICAL risk)
    ↓
    Yes → CLI Confirmation Prompt → User Confirms → Execute
    No → Execute Directly
```

#### Long-Term Memory

**Purpose**: Store and retrieve project context and historical data.

**Key Responsibilities**:
- Store command executions and outcomes
- Store project context for semantic retrieval
- Query by time range, intent type, or semantic similarity
- Support TTL for old data (default: 90 days)

**Design Decision**: Uses PostgreSQL for structured data (commands, outcomes) and vector store for semantic search (context, decisions). This separation optimizes for different query patterns.

**Memory Storage**:
```
Command Execution → PostgreSQL (structured)
    ↓
Context + Explanation → Vector Store (semantic)
    ↓
Query by Intent → PostgreSQL (exact match)
Query by Similarity → Vector Store (semantic search)
```

## Data Flow

### Simple Command Flow (Local Repair)

```
1. User enters: "sdlc stauts"
2. CLI Parser extracts: intent="stauts", target=null
3. Repair Engine detects typo (edit distance=1)
4. Repair Engine corrects: "stauts" → "status"
5. CLI displays: "✓ Corrected to: sdlc status"
6. CLI executes status command locally
7. CLI displays results
```

**Time**: < 50ms (no network calls)

### Complex Command Flow (Backend Required)

```
1. User enters: "show me security issues"
2. CLI Parser detects natural language
3. Repair Engine fails (confidence < 0.5)
4. CLI sends to Backend Intent Service
5. Backend queries Long-Term Memory
6. Backend uses LLM to infer: intent="analyze", target="security"
7. Backend creates execution plan
8. Agent Orchestrator invokes agents:
   - PlannerAgent: Create security analysis plan
   - ExecutorAgent: Run security scanners
   - ValidatorAgent: Verify results
   - ReflectionAgent: Analyze findings
9. Backend streams progress to CLI
10. CLI displays results with explanations
11. Backend updates SDLC state
12. Backend logs to audit trail
```

**Time**: 2-10s (depends on agent execution)

### High-Risk Command Flow

```
1. User enters: "sdlc release production"
2. CLI Parser extracts: intent="release", target="production"
3. CLI sends to Backend Policy Engine
4. Policy Engine classifies: risk=HIGH
5. Policy Engine returns: requiresConfirmation=true
6. CLI displays impact:
   "⚠️  HIGH RISK OPERATION
   This will deploy to PRODUCTION environment.
   Current state: STAGING
   Test coverage: 78.5%
   Open issues: 3 critical, 12 high
   
   Are you sure? (yes/no)"
7. User confirms: "yes"
8. CLI sends confirmation to Backend
9. Backend logs confirmation to audit trail
10. Agent Orchestrator executes release workflow
11. Backend streams progress to CLI
12. CLI displays results
```

**Time**: Variable (depends on user confirmation + execution)

## Design Principles

### 1. Local-First Performance

**Principle**: Deterministic operations should happen locally without network calls.

**Rationale**: 
- Faster response times (< 50ms vs. 500ms+)
- Works offline for basic operations
- Reduces backend load

**Implementation**:
- Command parsing: Local
- Typo correction (edit distance ≤ 2): Local
- Flag normalization: Local
- Intent inference: Backend
- Agent orchestration: Backend

### 2. Interface-Based Abstraction

**Principle**: Depend on interfaces, not concrete implementations.

**Rationale**:
- Testability (mock dependencies)
- Flexibility (swap implementations)
- Modularity (clear contracts)

**Implementation**:
- `LLMProvider` interface (no hard dependency on OpenAI, Anthropic, etc.)
- `Agent` interface (all agents implement same contract)
- `VectorStore` interface (pluggable vector store implementations)

### 3. Explainability First

**Principle**: Every decision must include traceable reasoning.

**Rationale**:
- Builds trust with users
- Enables learning and improvement
- Facilitates debugging

**Implementation**:
- Command repairs include explanations
- Intent inferences include confidence + reasoning
- Agent plans include step-by-step reasoning
- All decisions logged to audit trail

### 4. Safety by Default

**Principle**: High-risk operations require explicit confirmation.

**Rationale**:
- Prevents accidental damage
- Provides clear impact information
- Creates audit trail for compliance

**Implementation**:
- Policy Engine classifies all commands by risk
- HIGH/CRITICAL risk requires confirmation
- Confirmation includes impact assessment
- All confirmations logged with timestamp + user

### 5. Incremental Complexity

**Principle**: Start simple, add complexity only when needed.

**Rationale**:
- Easier to understand and maintain
- Faster initial development
- Can validate each component independently

**Implementation**:
- CLI skeleton → Command grammar → Deterministic repair → Intent inference → Agents → State tracking
- Each stage is functional and testable
- No placeholder logic or TODOs in production code

## Technology Stack

### CLI (Go)

**Framework**: Cobra
- **Why**: Industry standard for Go CLIs (used by kubectl, hugo, etc.)
- **Alternatives considered**: urfave/cli (less feature-rich)

**Testing**: gopter (property-based testing)
- **Why**: Mature property testing library for Go
- **Alternatives considered**: rapid (newer, less mature)

### Backend (Spring Boot)

**Framework**: Spring Boot 3.2 + Java 17
- **Why**: Mature ecosystem, excellent dependency injection, production-ready
- **Alternatives considered**: Quarkus (less mature ecosystem)

**Database**: PostgreSQL 14+
- **Why**: ACID guarantees, JSONB support, excellent performance
- **Alternatives considered**: MySQL (weaker JSON support)

**Migrations**: Flyway
- **Why**: Industry standard, version control for database
- **Alternatives considered**: Liquibase (more complex)

**Testing**: JUnit 5 + jqwik (property-based testing)
- **Why**: jqwik is the most mature property testing library for Java
- **Alternatives considered**: QuickTheories (less active)

### Communication

**Protocol**: JSON over HTTP + Server-Sent Events (SSE)
- **Why**: Simple, widely supported, good for streaming
- **Alternatives considered**: gRPC (more complex), WebSocket (overkill)

## Scalability Considerations

### CLI Scalability

**Concern**: Multiple users running CLI simultaneously

**Solution**:
- CLI is stateless (no shared state between invocations)
- Each CLI instance communicates independently with backend
- Backend handles concurrency

### Backend Scalability

**Concern**: Multiple concurrent agent executions

**Solution**:
- Agent Orchestrator supports concurrent execution (configurable limit)
- Database connection pooling (HikariCP)
- Optimistic locking for state updates
- Async processing for long-running operations

**Configuration**:
```yaml
sdlcraft:
  agent:
    max-concurrent-executions: 10
    timeout-seconds: 300
```

### Database Scalability

**Concern**: High volume of command executions and audit logs

**Solution**:
- Indexed queries (project_id, user_id, timestamp)
- TTL for old data (default: 90 days)
- Partitioning for audit logs (by timestamp)
- Read replicas for query-heavy workloads

### Vector Store Scalability

**Concern**: Large volume of context embeddings

**Solution**:
- Pluggable vector store interface (can use managed services)
- Batch embedding operations
- Caching for frequently accessed contexts
- Approximate nearest neighbor search (ANN)

## Future Considerations

### Distributed Deployment

**Current**: Single backend instance  
**Future**: Multiple backend instances with load balancer

**Challenges**:
- Agent execution coordination (use distributed locks)
- State consistency (use distributed cache)
- Event streaming (use message queue)

### Multi-Tenancy

**Current**: Single project per backend  
**Future**: Multiple projects/teams per backend

**Challenges**:
- Isolation (separate databases or schemas)
- Resource limits (per-project quotas)
- Authentication (integrate with SSO)

### Plugin System

**Current**: Intents and agents registered at compile time  
**Future**: Dynamic plugin loading at runtime

**Challenges**:
- Security (sandboxing untrusted plugins)
- Versioning (plugin compatibility)
- Discovery (plugin registry)

## Conclusion

SDLCraft CLI's architecture is designed for **simplicity, performance, and extensibility**. The local-first CLI ensures fast response times, while the backend provides powerful AI-driven orchestration. Interface-based abstractions enable flexibility and testability, and the incremental development path ensures each component can be validated independently.

The system is production-ready from day one, with comprehensive error handling, audit logging, and safety checks. Future enhancements can be added without disrupting the core architecture.
