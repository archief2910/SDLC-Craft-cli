# Design Document: SDLCraft CLI

## Overview

SDLCraft CLI is an intent-aware, self-healing SDLC orchestration engine that acts as a compiler for developer intent. The system consists of two primary components: a Go-based CLI that handles local command parsing and repair, and a Spring Boot backend that manages intent inference, agent orchestration, and SDLC state tracking.

The architecture follows a local-first design philosophy where deterministic operations happen in the CLI without network calls, while complex AI-powered operations and state management occur in the backend. This separation ensures fast response times for common operations while maintaining the power of intelligent orchestration for complex workflows.

The system is built around three core concepts:
1. **Self-Healing Commands**: Never fail on typos or imperfect syntax - always attempt repair
2. **Intent-First Interaction**: Developers express goals, not memorize syntax
3. **Agentic Execution**: Complex workflows are delegated to autonomous agents following PLAN → ACT → OBSERVE → REFLECT

## Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                         Developer                            │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                      CLI (Go)                                │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Command Parser                                       │  │
│  │  - Raw input parsing                                  │  │
│  │  - Grammar validation                                 │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Command Repair Engine (Deterministic)               │  │
│  │  - Typo correction (edit distance)                   │  │
│  │  - Flag normalization                                 │  │
│  │  - Ordering fixes                                     │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Output Renderer                                      │  │
│  │  - Streaming output                                   │  │
│  │  - Progress indicators                                │  │
│  │  - Confirmation prompts                               │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────────┬────────────────────────────────────┘
                         │ JSON over HTTP/IPC
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                  Backend (Spring Boot)                       │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Intent Inference Service                            │  │
│  │  - Natural language → structured intent              │  │
│  │  - LLM abstraction layer                             │  │
│  │  - Context retrieval from memory                     │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Agent Orchestrator                                  │  │
│  │  - Multi-agent coordination                          │  │
│  │  - PLAN → ACT → OBSERVE → REFLECT                    │  │
│  │  - Execution context management                      │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  SDLC State Machine                                  │  │
│  │  - Phase tracking                                     │  │
│  │  - Risk assessment                                    │  │
│  │  - Coverage metrics                                   │  │
│  │  - Release readiness                                  │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Policy Engine                                       │  │
│  │  - Risk classification                                │  │
│  │  - Confirmation requirements                          │  │
│  │  - Safety checks                                      │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                    Persistence Layer                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │  PostgreSQL  │  │ Vector Store │  │  Audit Logs      │  │
│  │  - State     │  │ - Context    │  │  - Commands      │  │
│  │  - Tasks     │  │ - Memory     │  │  - Decisions     │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Component Interaction Flow

**Simple Command Flow (Local Repair)**:
```
User Input → CLI Parser → Repair Engine → Execute Locally → Display Result
```

**Complex Command Flow (Backend Required)**:
```
User Input → CLI Parser → Repair Engine (fails) → Backend Intent Service
→ Agent Orchestrator → Agents (PLAN/ACT/OBSERVE/REFLECT) → State Update
→ Stream Results to CLI → Display to User
```

**High-Risk Command Flow**:
```
User Input → CLI Parser → Backend Policy Engine → Risk Assessment
→ CLI Confirmation Prompt → User Confirms → Agent Execution → Audit Log
```

## Components and Interfaces

### CLI Component (Go)

#### Command Parser

**Responsibility**: Parse raw user input into structured command objects.

**Interface**:
```go
type Command struct {
    Raw       string
    Intent    string
    Target    string
    Modifiers map[string]string
    IsValid   bool
}

type Parser interface {
    Parse(input string) (*Command, error)
    ValidateGrammar(cmd *Command) error
}
```

**Implementation Details**:
- Use regex patterns to extract intent, target, and modifiers
- Support both structured (`sdlc <intent> <target>`) and natural language input
- Mark commands as valid/invalid based on grammar rules
- Preserve original input for repair engine

#### Command Repair Engine

**Responsibility**: Deterministically repair invalid commands using edit distance and pattern matching.

**Interface**:
```go
type RepairResult struct {
    Original   string
    Repaired   *Command
    Confidence float64
    Explanation string
    Candidates []*Command
}

type RepairEngine interface {
    Repair(cmd *Command) (*RepairResult, error)
    SuggestCorrections(cmd *Command) ([]*Command, error)
}
```

**Implementation Details**:
- Use Levenshtein distance for typo detection (threshold: distance ≤ 2)
- Maintain a dictionary of valid intents and common targets
- Apply fuzzy matching for intent and target fields
- Return single result if confidence > 0.9, multiple candidates if 0.5-0.9, fail if < 0.5
- Never modify user input without showing the correction

**Repair Strategies**:
1. **Typo Correction**: Edit distance matching against known intents
2. **Flag Normalization**: Convert `--flag`, `-f`, `flag` to standard form
3. **Ordering Fixes**: Reorder arguments to match grammar
4. **Synonym Expansion**: Map common synonyms (e.g., "check" → "status")

#### Backend Client

**Responsibility**: Communicate with Spring Boot backend for intent inference and agent execution.

**Interface**:
```go
type IntentRequest struct {
    Command     *Command
    Context     map[string]interface{}
    UserID      string
    ProjectPath string
}

type IntentResponse struct {
    Intent      string
    Target      string
    Plan        []string
    RequiresConfirmation bool
    RiskLevel   string
    Explanation string
}

type BackendClient interface {
    InferIntent(req *IntentRequest) (*IntentResponse, error)
    ExecuteIntent(req *IntentRequest) (chan ExecutionEvent, error)
    QueryState() (*SDLCState, error)
}
```

**Implementation Details**:
- Use HTTP client with configurable timeout (default: 30s)
- Support both HTTP and Unix socket communication
- Implement retry logic with exponential backoff
- Stream execution events using Server-Sent Events (SSE) or WebSocket

#### Output Renderer

**Responsibility**: Display results, progress, and prompts to the user.

**Interface**:
```go
type Renderer interface {
    DisplayResult(result interface{})
    DisplayProgress(event ExecutionEvent)
    PromptConfirmation(message string, riskLevel string) (bool, error)
    DisplayError(err error)
}
```

**Implementation Details**:
- Use color-coded output (green for success, yellow for warnings, red for errors)
- Show spinner for long-running operations
- Display agent reasoning in verbose mode
- Format confirmation prompts with clear risk indicators

### Backend Component (Spring Boot)

#### Intent Inference Service

**Responsibility**: Convert natural language and ambiguous commands into structured intents.

**Interface**:
```java
public interface IntentInferenceService {
    IntentResult inferIntent(IntentRequest request);
    List<Intent> getSupportedIntents();
    void registerIntent(IntentDefinition definition);
}

public class IntentResult {
    private String intent;
    private String target;
    private Map<String, String> modifiers;
    private double confidence;
    private String explanation;
    private List<String> clarificationQuestions;
}
```

**Implementation Details**:
- Use LLM abstraction layer (interface-based, no hard dependency)
- Query long-term memory for similar past commands
- Apply intent templates for common patterns
- Return clarification questions if confidence < 0.7
- Cache intent mappings for frequently used phrases

**LLM Abstraction**:
```java
public interface LLMProvider {
    String complete(String prompt, Map<String, Object> parameters);
    List<String> embed(List<String> texts);
}
```

#### Agent Orchestrator

**Responsibility**: Coordinate multi-agent workflows following PLAN → ACT → OBSERVE → REFLECT pattern.

**Interface**:
```java
public interface AgentOrchestrator {
    ExecutionPlan createPlan(IntentResult intent, SDLCState state);
    ExecutionResult execute(ExecutionPlan plan);
    void registerAgent(String type, Agent agent);
}

public interface Agent {
    AgentResult plan(AgentContext context);
    AgentResult act(AgentContext context, Plan plan);
    AgentResult observe(AgentContext context, ActionResult result);
    AgentResult reflect(AgentContext context, ObservationResult observation);
}
```

**Implementation Details**:
- Maintain execution context across agent transitions
- Support parallel agent execution where dependencies allow
- Implement timeout and cancellation for long-running agents
- Store execution traces for debugging and learning

**Agent Types**:

1. **PlannerAgent**: Creates execution plans from intents
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

#### SDLC State Machine

**Responsibility**: Track and manage project SDLC state.

**Interface**:
```java
public interface SDLCStateMachine {
    SDLCState getCurrentState(String projectId);
    void transitionTo(String projectId, Phase newPhase);
    void updateMetrics(String projectId, Metrics metrics);
    ReleaseReadiness calculateReadiness(String projectId);
}

public class SDLCState {
    private Phase currentPhase;
    private RiskLevel riskLevel;
    private double testCoverage;
    private int openIssues;
    private LocalDateTime lastDeployment;
    private Map<String, Object> customMetrics;
}

public enum Phase {
    PLANNING, DEVELOPMENT, TESTING, STAGING, PRODUCTION
}
```

**Implementation Details**:
- Persist state changes to PostgreSQL with timestamps
- Calculate risk scores based on multiple factors (coverage, issues, time since deploy)
- Support custom metrics registration
- Emit events on state transitions for audit logging

**Risk Calculation**:
```
Risk Score = (
    (1 - testCoverage) * 0.4 +
    (openIssues / totalIssues) * 0.3 +
    (daysSinceLastDeploy / 30) * 0.2 +
    customRiskFactors * 0.1
)
```

#### Policy Engine

**Responsibility**: Enforce safety policies and determine confirmation requirements.

**Interface**:
```java
public interface PolicyEngine {
    RiskAssessment assessRisk(IntentResult intent, SDLCState state);
    boolean requiresConfirmation(RiskAssessment assessment);
    List<PolicyViolation> checkPolicies(IntentResult intent);
}

public class RiskAssessment {
    private RiskLevel level;
    private List<String> concerns;
    private String explanation;
    private boolean requiresConfirmation;
}

public enum RiskLevel {
    LOW, MEDIUM, HIGH, CRITICAL
}
```

**Implementation Details**:
- Define policies as rules (e.g., "production deployments require confirmation")
- Support custom policy registration
- Log all policy checks and decisions
- Block execution if critical policies are violated

**Risk Classification Rules**:
- **HIGH**: Operations on production, delete operations, reset operations
- **MEDIUM**: Operations on staging, bulk updates, schema changes
- **LOW**: Read operations, analysis, status checks

#### Long-Term Memory

**Responsibility**: Store and retrieve project context and historical data.

**Interface**:
```java
public interface LongTermMemory {
    void storeCommand(CommandExecution execution);
    List<CommandExecution> queryByIntent(String intent, int limit);
    List<CommandExecution> querySimilar(String query, int limit);
    void storeContext(String projectId, Map<String, Object> context);
    Map<String, Object> retrieveContext(String projectId);
}
```

**Implementation Details**:
- Use PostgreSQL for structured data (commands, outcomes, timestamps)
- Use vector store for semantic search (context, decisions, explanations)
- Implement TTL for old data (configurable, default: 90 days)
- Support export/import for backup and migration

## Data Models

### Command Model

```go
type Command struct {
    ID          string
    Raw         string
    Intent      string
    Target      string
    Modifiers   map[string]string
    IsValid     bool
    Timestamp   time.Time
    UserID      string
    ProjectPath string
}
```

### Intent Model

```java
public class Intent {
    private String name;
    private String description;
    private List<String> requiredParameters;
    private List<String> optionalParameters;
    private List<String> examples;
    private RiskLevel defaultRiskLevel;
}
```

### Execution Plan Model

```java
public class ExecutionPlan {
    private String id;
    private String intent;
    private List<Step> steps;
    private Map<String, Object> context;
    private LocalDateTime createdAt;
}

public class Step {
    private String id;
    private String agentType;
    private String action;
    private Map<String, Object> parameters;
    private List<String> dependencies;
}
```

### SDLC State Model

```java
@Entity
@Table(name = "sdlc_state")
public class SDLCStateEntity {
    @Id
    private String projectId;
    
    @Enumerated(EnumType.STRING)
    private Phase currentPhase;
    
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;
    
    private Double testCoverage;
    private Integer openIssues;
    private LocalDateTime lastDeployment;
    
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> customMetrics;
    
    private LocalDateTime updatedAt;
}
```

### Command Execution Model

```java
@Entity
@Table(name = "command_executions")
public class CommandExecution {
    @Id
    private String id;
    
    private String userId;
    private String projectId;
    private String rawCommand;
    private String intent;
    private String target;
    
    @Column(columnDefinition = "jsonb")
    private Map<String, String> modifiers;
    
    @Enumerated(EnumType.STRING)
    private ExecutionStatus status;
    
    @Column(columnDefinition = "text")
    private String outcome;
    
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long durationMs;
}
```

### Agent Context Model

```java
public class AgentContext {
    private String executionId;
    private String intent;
    private SDLCState currentState;
    private Map<String, Object> parameters;
    private List<AgentResult> previousResults;
    private LocalDateTime deadline;
}
```


## Correctness Properties

A property is a characteristic or behavior that should hold true across all valid executions of a system - essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.

### Property 1: Command Repair Attempts

*For any* command with typos (edit distance ≤ 2 from valid intent), the CLI repair engine should attempt correction using edit distance algorithms and either return a repaired command or invoke the intent inference service.

**Validates: Requirements 1.1, 1.4**

### Property 2: Single Candidate Auto-Correction

*For any* command where repair produces a single candidate with confidence > 0.9, the CLI should auto-correct and display the corrected command without requiring user selection.

**Validates: Requirements 1.2**

### Property 3: Multiple Candidate Presentation

*For any* command where repair produces multiple candidates with confidence between 0.5-0.9, the CLI should present all candidates to the user for selection.

**Validates: Requirements 1.3**

### Property 4: Ambiguity Clarification

*For any* command that is ambiguous (multiple valid interpretations), the CLI should ask clarifying questions before execution rather than guessing intent.

**Validates: Requirements 1.5**

### Property 5: Helpful Error Messages

*For any* invalid command that cannot be repaired or interpreted, the CLI should provide a specific, helpful error message with suggestions, never failing silently or with generic "unknown command" errors.

**Validates: Requirements 1.6, 1.7**

### Property 6: Grammar Pattern Parsing

*For any* input matching the pattern `sdlc <intent> <target> [modifiers]`, the CLI parser should successfully extract intent, target, and modifiers into a structured Command object.

**Validates: Requirements 2.1**

### Property 7: Natural Language to Structured Intent

*For any* natural language input, the Intent Inference Service should map it to a structured intent with intent name, target, modifiers, and confidence score.

**Validates: Requirements 2.3**

### Property 8: Intent Registration and Recognition

*For any* newly registered intent, the CLI should recognize and parse commands using that intent after registration.

**Validates: Requirements 2.4, 11.1**

### Property 9: Missing Target Prompting

*For any* intent that requires a target, if the target is not provided in the command, the CLI should prompt the user for the missing information before execution.

**Validates: Requirements 2.5**

### Property 10: Invalid Intent-Target Rejection

*For any* invalid intent-target combination, the CLI should reject the command with an explanation of why the combination is invalid.

**Validates: Requirements 2.6**

### Property 11: State Persistence Round-Trip

*For any* SDLC state change (phase transition, metric update, risk assessment), persisting to PostgreSQL and then querying should return an equivalent state object.

**Validates: Requirements 3.5, 5.1**

### Property 12: Release Readiness Calculation

*For any* project state with defined test coverage, open issues, and deployment history, the SDLC State Machine should calculate a release readiness score using the defined formula.

**Validates: Requirements 3.4**

### Property 13: Status Response Completeness

*For any* status query, the Backend response should include all required fields: project phase, risk level, test coverage, and release readiness.

**Validates: Requirements 3.7, 10.4**

### Property 14: Agent Execution Order with Context Preservation

*For any* multi-step intent execution, agents should be invoked in the order PLAN → ACT → OBSERVE → REFLECT, and execution context should be available to all agents in the sequence.

**Validates: Requirements 4.1, 4.4, 4.5**

### Property 15: Multi-Agent Coordination

*For any* intent requiring multiple steps, the Agent Orchestrator should coordinate all necessary agents and maintain workflow state across transitions.

**Validates: Requirements 4.3**

### Property 16: Error Reflection

*For any* agent execution that encounters an error, the ReflectionAgent should be invoked to analyze the failure and suggest recovery actions.

**Validates: Requirements 4.6**

### Property 17: Progress Streaming

*For any* agent execution, progress events should be streamed to the CLI in real-time as they occur, not batched at the end.

**Validates: Requirements 4.7**

### Property 18: Memory Query Retrieval

*For any* intent processing, the Intent Inference Service should query Long-Term Memory for relevant historical context before creating the execution plan.

**Validates: Requirements 5.3**

### Property 19: Historical Reference in Planning

*For any* intent that was executed previously with similar parameters, the Backend should reference past outcomes when creating the current execution plan.

**Validates: Requirements 5.5**

### Property 20: Memory Query Capabilities

*For any* memory query by time range, intent type, or semantic similarity, the Long-Term Memory should return results matching the query criteria.

**Validates: Requirements 5.6**

### Property 21: Vector Store Round-Trip

*For any* project context stored in the vector store, storing and then retrieving by semantic similarity should return the original context.

**Validates: Requirements 5.2**

### Property 22: Risk Classification Correctness

*For any* command, the Policy Engine should classify it as LOW risk (read operations), MEDIUM risk (staging operations), or HIGH risk (production/delete/reset operations) according to the defined rules.

**Validates: Requirements 6.1**

### Property 23: High-Risk Confirmation Requirement

*For any* high-risk command (production, delete, reset), the CLI should require explicit user confirmation before execution, and execution should be blocked without confirmation.

**Validates: Requirements 6.2, 6.5, 6.6**

### Property 24: Impact Display Before Confirmation

*For any* high-risk command requiring confirmation, the CLI should display the potential impact information before requesting confirmation.

**Validates: Requirements 6.3**

### Property 25: Confirmation Audit Logging

*For any* confirmed high-risk action, the Backend should create an audit log entry containing the confirmation, timestamp, and user context.

**Validates: Requirements 6.4, 5.4**

### Property 26: Explanation Presence

*For any* command repair, intent inference, or suggestion, the system should include an explanation of the reasoning behind the action.

**Validates: Requirements 7.1, 7.3, 7.5**

### Property 27: Plan Reasoning Completeness

*For any* execution plan created by an agent, each step in the plan should include reasoning explaining why that step is necessary.

**Validates: Requirements 7.2**

### Property 28: Decision Traceability

*For any* decision made by the Backend (intent inference, risk assessment, plan creation), there should be traceable reasoning stored with the decision.

**Validates: Requirements 7.6**

### Property 29: Local-First Repair

*For any* command that can be repaired deterministically (typos with edit distance ≤ 2), the CLI should complete the repair without making Backend calls.

**Validates: Requirements 8.2, 8.3**

### Property 30: JSON Communication Protocol

*For any* message sent between CLI and Backend, the message should be valid JSON and conform to the defined request/response schemas.

**Validates: Requirements 8.4**

### Property 31: Backend Unavailability Handling

*For any* Backend request that fails due to unavailability, the CLI should handle the failure gracefully and display an informative error message rather than crashing.

**Validates: Requirements 8.5**

### Property 32: Cache Effectiveness

*For any* data that is cached by the CLI, repeated requests for the same data should use the cache and not make additional Backend calls.

**Validates: Requirements 8.6**

### Property 33: Progressive Output Streaming

*For any* long-running execution, output should appear in the terminal progressively as it's generated, not all at once at the end.

**Validates: Requirements 8.7**

### Property 34: LLM Provider Swappability

*For any* LLM provider implementation conforming to the LLMProvider interface, the Backend should be able to use it without code changes to the Intent Inference Service.

**Validates: Requirements 9.5**

### Property 35: Structured Error Responses

*For any* error condition in the Backend, the error response should be structured JSON containing error code, message, and details.

**Validates: Requirements 9.6**

### Property 36: Concurrent Agent Execution Safety

*For any* set of agents that can execute concurrently (no dependencies), running them in parallel should produce the same final state as running them sequentially.

**Validates: Requirements 9.7**

### Property 37: Security Analysis Agent Coordination

*For any* "analyze security" intent execution, the Agent Orchestrator should invoke security scanning agents and aggregate their results.

**Validates: Requirements 10.5**

### Property 38: Performance Analysis and Suggestions

*For any* "improve performance" intent execution, the Agent Orchestrator should identify bottlenecks and return optimization suggestions.

**Validates: Requirements 10.6**

### Property 39: Custom Agent Interface Compliance

*For any* custom agent implementing the Agent interface, the Agent Orchestrator should be able to register and invoke it following the PLAN → ACT → OBSERVE → REFLECT pattern.

**Validates: Requirements 11.2**

### Property 40: Dynamic Agent Registration

*For any* agent registered at runtime, the Agent Orchestrator should make it available for intent execution without requiring system restart.

**Validates: Requirements 11.3**

### Property 41: Configuration-Based Intent Loading

*For any* intent definition added to configuration, the Intent Inference Service should load and recognize it on startup or configuration reload.

**Validates: Requirements 11.4**

### Property 42: CLI Independent Testability

*For any* CLI component test, the test should execute successfully without requiring a running Backend instance.

**Validates: Requirements 13.3**

### Property 43: Backend Independent Testability

*For any* Backend component test, the test should execute successfully without requiring the CLI.

**Validates: Requirements 13.4**

### Property 44: Incremental Intent Deployment

*For any* new intent or agent, deploying it should not require redeploying or restarting existing intents and agents.

**Validates: Requirements 13.5**

## Error Handling

### CLI Error Handling

**Command Parsing Errors**:
- Invalid grammar → Attempt repair, show suggestions
- Unknown intent → Fuzzy match against known intents, suggest closest matches
- Missing required parameters → Prompt for missing values
- Invalid parameter values → Show validation error with expected format

**Backend Communication Errors**:
- Connection timeout → Retry with exponential backoff (3 attempts), then fail gracefully
- Backend unavailable → Show error with suggestion to check backend status
- Invalid response → Log error details, show user-friendly message
- Network errors → Detect and report with troubleshooting steps

**Execution Errors**:
- Agent failure → Show agent error message, invoke ReflectionAgent for analysis
- Timeout → Allow user to cancel or extend timeout
- Resource unavailable → Show specific resource and suggested actions

### Backend Error Handling

**Intent Inference Errors**:
- LLM provider unavailable → Fall back to template-based inference
- Low confidence inference → Return clarification questions
- Ambiguous intent → Return multiple interpretations for user selection
- Invalid context → Request additional context from user

**Agent Execution Errors**:
- Agent crash → Log stack trace, invoke ReflectionAgent, suggest recovery
- Dependency failure → Identify failed dependency, suggest resolution
- Timeout → Mark step as failed, allow retry or skip
- Resource exhaustion → Queue execution for later, notify user

**State Management Errors**:
- Database connection failure → Retry with backoff, use cached state if available
- Concurrent modification → Use optimistic locking, retry transaction
- Invalid state transition → Reject with explanation of valid transitions
- Data corruption → Log error, attempt recovery from audit log

**Policy Violations**:
- High-risk without confirmation → Block execution, require confirmation
- Invalid permissions → Return permission error with required permissions
- Rate limit exceeded → Return rate limit error with retry-after time

### Error Response Format

All Backend errors follow this structure:

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable error message",
    "details": {
      "field": "Additional context",
      "suggestion": "Recommended action"
    },
    "timestamp": "2024-01-01T12:00:00Z",
    "requestId": "unique-request-id"
  }
}
```

## Testing Strategy

### Dual Testing Approach

The system requires both unit testing and property-based testing for comprehensive coverage:

**Unit Tests**: Verify specific examples, edge cases, and error conditions
- Specific command repair examples (e.g., "stauts" → "status")
- Edge cases (empty input, very long input, special characters)
- Error conditions (backend unavailable, invalid JSON, timeout)
- Integration points between CLI and Backend
- Specific intent implementations (status, analyze, improve)

**Property Tests**: Verify universal properties across all inputs
- Command repair for any typo within edit distance 2
- State persistence round-trip for any state object
- Agent execution order for any multi-step workflow
- Risk classification for any command type
- Explanation presence for any inference or suggestion

Both testing approaches are complementary and necessary. Unit tests catch concrete bugs in specific scenarios, while property tests verify general correctness across the input space.

### Property-Based Testing Configuration

**Framework Selection**:
- **Go (CLI)**: Use [gopter](https://github.com/leanovate/gopter) for property-based testing
- **Java (Backend)**: Use [jqwik](https://jqwik.net/) for property-based testing

**Test Configuration**:
- Minimum 100 iterations per property test (due to randomization)
- Each property test must reference its design document property
- Tag format: `Feature: sdlcraft-cli, Property {number}: {property_text}`

**Example Property Test Structure (Go)**:

```go
// Feature: sdlcraft-cli, Property 1: Command Repair Attempts
func TestProperty_CommandRepairAttempts(t *testing.T) {
    properties := gopter.NewProperties(nil)
    
    properties.Property("repairs commands with typos", prop.ForAll(
        func(validIntent string, typo string) bool {
            // Generate command with intentional typo
            cmd := generateCommandWithTypo(validIntent, typo)
            
            // Attempt repair
            result, err := repairEngine.Repair(cmd)
            
            // Verify repair was attempted
            return err == nil && (result.Repaired != nil || result.InferenceServiceCalled)
        },
        gen.OneConstOf("status", "analyze", "improve", "test", "debug"),
        gen.Identifier(),
    ))
    
    properties.TestingRun(t, gopter.ConsoleReporter(false))
}
```

**Example Property Test Structure (Java)**:

```java
// Feature: sdlcraft-cli, Property 11: State Persistence Round-Trip
@Property
void statePersistenceRoundTrip(@ForAll("sdlcStates") SDLCState originalState) {
    // Persist state
    stateMachine.updateState(PROJECT_ID, originalState);
    
    // Retrieve state
    SDLCState retrievedState = stateMachine.getCurrentState(PROJECT_ID);
    
    // Verify equivalence
    assertThat(retrievedState).isEqualToIgnoringGivenFields(originalState, "updatedAt");
}

@Provide
Arbitrary<SDLCState> sdlcStates() {
    return Combinators.combine(
        Arbitraries.of(Phase.values()),
        Arbitraries.of(RiskLevel.values()),
        Arbitraries.doubles().between(0.0, 1.0),
        Arbitraries.integers().between(0, 100)
    ).as((phase, risk, coverage, issues) -> 
        new SDLCState(phase, risk, coverage, issues)
    );
}
```

### Test Coverage Requirements

**CLI Component**:
- Command parser: 100% coverage of grammar patterns
- Repair engine: All repair strategies (typo, flag, ordering, synonym)
- Backend client: All request/response types
- Output renderer: All output formats (success, error, progress, confirmation)

**Backend Component**:
- Intent inference: All supported intents and natural language patterns
- Agent orchestrator: All agent types and workflow patterns
- State machine: All phase transitions and metric calculations
- Policy engine: All risk levels and policy rules
- Long-term memory: All query types (time, intent, semantic)

### Integration Testing

**CLI-Backend Integration**:
- End-to-end command flow from input to execution
- Streaming output during long-running operations
- Error propagation from backend to CLI
- Confirmation flow for high-risk commands

**Backend-Database Integration**:
- State persistence and retrieval
- Audit log creation and querying
- Vector store operations
- Concurrent access and locking

**Agent Integration**:
- Multi-agent workflows
- Context passing between agents
- Error handling and reflection
- Timeout and cancellation

### Performance Testing

**CLI Performance**:
- Command parsing: < 10ms for any input
- Deterministic repair: < 50ms for any command
- Output rendering: < 5ms per line

**Backend Performance**:
- Intent inference: < 500ms for any input
- State query: < 100ms for any project
- Agent orchestration: < 5s for typical workflows
- Memory query: < 200ms for semantic search

**Load Testing**:
- Concurrent CLI connections: Support 100+ simultaneous users
- Agent execution: Support 10+ concurrent workflows
- Database operations: Handle 1000+ transactions per second

### Test Data Generation

**Command Generators**:
- Valid commands following grammar
- Commands with typos (edit distance 1-3)
- Natural language variations
- Edge cases (empty, very long, special characters)

**State Generators**:
- All phase combinations
- Risk levels with corresponding metrics
- Valid and invalid state transitions
- Boundary conditions (0% coverage, 100% coverage)

**Agent Context Generators**:
- Various intent types
- Different execution histories
- Success and failure scenarios
- Timeout conditions

### Continuous Testing

**Pre-commit Hooks**:
- Run unit tests for changed components
- Run property tests for changed components
- Verify code formatting and linting

**CI Pipeline**:
- Run full unit test suite
- Run full property test suite (100 iterations)
- Run integration tests
- Generate coverage reports
- Performance regression tests

**Nightly Builds**:
- Extended property tests (1000 iterations)
- Load testing
- Security scanning
- Dependency vulnerability checks
