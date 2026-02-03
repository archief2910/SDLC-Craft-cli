# Implementation Plan: SDLCraft CLI

## Overview

This implementation plan builds SDLCraft CLI incrementally, starting with the CLI skeleton and command grammar, then adding deterministic repair, followed by the backend services, agent framework, and finally SDLC state tracking. Each task builds on previous work, ensuring the system remains functional at each stage.

The implementation uses Go for the CLI (with Cobra framework) and Java/Spring Boot for the backend, as specified in the requirements.

## Tasks

- [x] 1. Set up monorepo structure and project scaffolding
  - Create monorepo with `cli/` and `backend/` directories
  - Initialize Go module for CLI with Cobra framework
  - Initialize Spring Boot project (Java 17+) with Maven/Gradle
  - Set up PostgreSQL schema for SDLC state and audit logs
  - Create shared documentation (README, architecture diagrams)
  - _Requirements: 12.7_

- [ ] 2. Implement CLI command parser and grammar
  - [x] 2.1 Create Command data structure and Parser interface
    - Define Command struct with Raw, Intent, Target, Modifiers fields
    - Implement Parser interface with Parse() and ValidateGrammar() methods
    - _Requirements: 2.1_
  
  - [x] 2.2 Implement grammar pattern parsing
    - Parse `sdlc <intent> <target> [modifiers]` pattern using regex
    - Extract intent, target, and modifiers into Command struct
    - Support both structured and natural language input detection
    - _Requirements: 2.1_
  
  - [x] 2.3 Write property test for grammar parsing

    - **Property 6: Grammar Pattern Parsing**
    - **Validates: Requirements 2.1**
  
  - [x] 2.4 Write unit tests for parser edge cases

    - Test empty input, very long input, special characters
    - Test various modifier formats (--flag, -f, flag)
    - _Requirements: 2.1_

- [x] 3. Implement deterministic command repair engine
  - [x] 3.1 Create RepairEngine interface and implementation
    - Define RepairResult struct with Original, Repaired, Confidence, Explanation
    - Implement Levenshtein distance algorithm for typo detection
    - Build dictionary of valid intents and common targets
    - _Requirements: 1.1_
  
  - [x] 3.2 Implement repair strategies
    - Typo correction using edit distance (threshold ≤ 2)
    - Flag normalization (convert various formats to standard)
    - Argument ordering fixes
    - Synonym expansion (e.g., "check" → "status")
    - _Requirements: 1.1, 1.2_
  
  - [x] 3.3 Implement confidence-based decision logic
    - Auto-correct for confidence > 0.9 (single candidate)
    - Present options for confidence 0.5-0.9 (multiple candidates)
    - Fail gracefully for confidence < 0.5
    - _Requirements: 1.2, 1.3_
  
  - [x] 3.4 Write property test for command repair

    - **Property 1: Command Repair Attempts**
    - **Validates: Requirements 1.1, 1.4**
  
  - [x] 3.5 Write property test for auto-correction

    - **Property 2: Single Candidate Auto-Correction**
    - **Validates: Requirements 1.2**
  
  - [x] 3.6 Write property test for multiple candidates

    - **Property 3: Multiple Candidate Presentation**
    - **Validates: Requirements 1.3**

- [x] 4. Implement CLI output renderer and user interaction
  - [x] 4.1 Create Renderer interface and implementation
    - Define Renderer interface with DisplayResult, DisplayProgress, PromptConfirmation methods
    - Implement color-coded output (green, yellow, red)
    - Support progress indicators for long-running operations
    - _Requirements: 8.7_
  
  - [x] 4.2 Implement confirmation prompts for high-risk commands
    - Display risk level and potential impact
    - Require explicit user confirmation (yes/no)
    - Support non-interactive mode for CI/CD
    - _Requirements: 6.2, 6.3_
  
  - [x] 4.3 Implement streaming output display
    - Stream execution events in real-time
    - Display agent reasoning in verbose mode
    - Handle progress updates and status changes
    - _Requirements: 4.7, 8.7_

- [x] 5. Implement CLI-Backend communication
  - [x] 5.1 Create BackendClient interface and HTTP implementation
    - Define IntentRequest and IntentResponse structures
    - Implement HTTP client with configurable timeout
    - Support both HTTP and Unix socket communication
    - _Requirements: 8.4_
  
  - [x] 5.2 Implement retry logic and error handling
    - Exponential backoff for retries (3 attempts)
    - Handle backend unavailability gracefully
    - Return structured error responses
    - _Requirements: 8.5_
  
  - [x] 5.3 Implement Server-Sent Events (SSE) for streaming
    - Stream execution events from backend to CLI
    - Handle connection interruptions
    - Support cancellation and timeout
    - _Requirements: 4.7, 8.7_
  
  - [x] 5.4 Write property test for JSON communication

    - **Property 30: JSON Communication Protocol**
    - **Validates: Requirements 8.4**

- [x] 6. Implement Backend Intent Inference Service
  - [x] 6.1 Create IntentInferenceService interface
    - Define IntentResult with intent, target, modifiers, confidence, explanation
    - Implement LLMProvider abstraction interface
    - Support intent template matching
    - _Requirements: 2.3, 9.5_
  
  - [x] 6.2 Implement natural language to structured intent mapping
    - Query long-term memory for similar commands
    - Apply intent templates for common patterns
    - Use LLM for complex natural language
    - Return clarification questions if confidence < 0.7
    - _Requirements: 2.3, 5.3_
  
  - [x] 6.3 Implement intent registration and extensibility
    - Load intent definitions from database
    - Support dynamic intent registration
    - Validate intent-target combinations
    - _Requirements: 2.4, 11.1, 11.4_
  
  - [x] 6.4 Write property test for natural language inference

    - **Property 7: Natural Language to Structured Intent**
    - **Validates: Requirements 2.3**

- [x] 7. Implement Backend SDLC State Machine
  - [x] 7.1 Create SDLCStateMachine interface and JPA entities
    - Define SDLCState entity with phase, risk, coverage, metrics
    - Implement state persistence with PostgreSQL
    - Create repository interfaces
    - _Requirements: 3.1, 3.5, 9.3_
  
  - [x] 7.2 Implement phase transition logic
    - Support phase transitions (PLANNING → DEVELOPMENT → TESTING → STAGING → PRODUCTION)
    - Validate state transitions
    - Emit events on state changes
    - _Requirements: 3.1_
  
  - [x] 7.3 Implement risk assessment and metrics tracking
    - Calculate risk scores based on coverage, issues, deployment history
    - Track test coverage metrics
    - Calculate release readiness scores
    - _Requirements: 3.2, 3.3, 3.4_
  
  - [x] 7.4 Implement state query APIs
    - REST endpoints for querying current state
    - Support filtering by project, phase, risk level
    - Return comprehensive state with metrics
    - _Requirements: 3.6, 3.7_
  
  - [x] 7.5 Write property test for state persistence

    - **Property 11: State Persistence Round-Trip**
    - **Validates: Requirements 3.5, 5.1**
  
  - [x] 7.6 Write property test for release readiness calculation

    - **Property 12: Release Readiness Calculation**
    - **Validates: Requirements 3.4**

- [x] 8. Implement Backend Policy Engine
  - [x] 8.1 Create PolicyEngine interface and implementation
    - Define RiskAssessment with level, concerns, explanation
    - Implement risk classification rules (LOW, MEDIUM, HIGH, CRITICAL)
    - Support custom policy registration
    - _Requirements: 6.1_
  
  - [x] 8.2 Implement confirmation requirement logic
    - Determine if command requires confirmation based on risk
    - Generate impact assessment for high-risk commands
    - Block execution without confirmation
    - _Requirements: 6.2, 6.5, 6.6_
  
  - [x] 8.3 Implement policy violation checking
    - Check commands against registered policies
    - Return policy violations with explanations
    - Log all policy checks and decisions
    - _Requirements: 6.1_
  
  - [x] 8.4 Write property test for risk classification

    - **Property 22: Risk Classification Correctness**
    - **Validates: Requirements 6.1**

- [x] 9. Implement Backend Agent Framework
  - [x] 9.1 Create Agent interface and AgentOrchestrator
    - Define Agent interface with plan(), act(), observe(), reflect() methods
    - Implement AgentOrchestrator for multi-agent coordination
    - Support agent registration and discovery
    - _Requirements: 4.1, 4.2, 11.2_
  
  - [x] 9.2 Implement PlannerAgent
    - Break down high-level intents into concrete steps
    - Identify required resources and dependencies
    - Estimate execution time and risk
    - Generate execution plans with reasoning
    - _Requirements: 4.1, 7.2_
  
  - [x] 9.3 Implement ExecutorAgent
    - Execute planned actions (commands, scripts, API calls)
    - Handle retries and error recovery
    - Report progress to orchestrator
    - _Requirements: 4.1_
  
  - [x] 9.4 Implement ValidatorAgent
    - Validate execution results against expected criteria
    - Verify state changes
    - Identify anomalies or failures
    - _Requirements: 4.1_
  
  - [x] 9.5 Implement ReflectionAgent
    - Analyze execution outcomes
    - Compare actual vs. expected results
    - Identify failure root causes
    - Suggest recovery actions or plan adjustments
    - _Requirements: 4.1, 4.6_
  
  - [x] 9.6 Implement agent execution context management
    - Maintain context across agent transitions
    - Support parallel agent execution where possible
    - Implement timeout and cancellation
    - Store execution traces for debugging
    - _Requirements: 4.4, 4.5, 9.7_
  
  - [x] 9.7 Write property test for agent execution order

    - **Property 14: Agent Execution Order with Context Preservation**
    - **Validates: Requirements 4.1, 4.4, 4.5**
  
  - [x] 9.8 Write property test for error reflection

    - **Property 16: Error Reflection**
    - **Validates: Requirements 4.6**

- [x] 10. Implement Backend Long-Term Memory
  - [x] 10.1 Create LongTermMemory interface and implementation
    - Define CommandExecution entity for history
    - Implement PostgreSQL storage for structured data
    - Create repository interfaces
    - _Requirements: 5.1, 9.3_
  
  - [x] 10.2 Implement vector store abstraction
    - Define VectorStore interface for pluggable implementations
    - Support storing and retrieving embeddings
    - Implement semantic similarity search
    - _Requirements: 5.2, 9.4_
  
  - [x] 10.3 Implement command execution history
    - Store all command executions with outcomes
    - Support querying by intent, time range, user
    - Implement TTL for old data (default: 90 days)
    - _Requirements: 5.1, 5.4_
  
  - [x] 10.4 Implement context storage and retrieval
    - Store project context in vector store
    - Support semantic similarity queries
    - Cache frequently accessed contexts
    - _Requirements: 5.2, 5.3_
  
  - [x] 10.5 Write property test for memory query capabilities

    - **Property 20: Memory Query Capabilities**
    - **Validates: Requirements 5.6**

- [x] 11. Implement Backend Audit Logging
  - [x] 11.1 Create audit log entities and repository
    - Define AuditLog entity with action, entity, old/new values
    - Implement PostgreSQL storage
    - Support JSONB for flexible data storage
    - _Requirements: 5.4, 9.3_
  
  - [x] 11.2 Implement audit event emission
    - Emit events on state changes
    - Log high-risk command confirmations
    - Record agent execution traces
    - _Requirements: 5.4, 6.4_
  
  - [x] 11.3 Implement audit query APIs
    - REST endpoints for querying audit logs
    - Support filtering by time, user, action, risk level
    - Return structured audit trail
    - _Requirements: 5.4_
  
  - [x] 11.4 Write property test for confirmation audit logging

    - **Property 25: Confirmation Audit Logging**
    - **Validates: Requirements 6.4, 5.4**

- [x] 12. Implement core intent handlers
  - [x] 12.1 Implement "status" intent
    - Query current SDLC state from state machine
    - Return phase, risk level, test coverage, release readiness
    - Support verbose mode with detailed metrics
    - _Requirements: 10.1, 10.4_
  
  - [x] 12.2 Implement "analyze security" intent
    - Coordinate security scanning agents
    - Aggregate security analysis results
    - Return findings with severity levels
    - _Requirements: 10.2, 10.5_
  
  - [x] 12.3 Implement "improve performance" intent
    - Identify performance bottlenecks
    - Generate optimization suggestions
    - Estimate impact of improvements
    - _Requirements: 10.3, 10.6_
  
  - [x] 12.4 Write property test for status response completeness

    - **Property 13: Status Response Completeness**
    - **Validates: Requirements 3.7, 10.4**

- [-] 13. Integration and end-to-end testing
  - [x] 13.1 Implement CLI-Backend integration tests
    - Test complete command flow from input to execution
    - Test streaming output during long-running operations
    - Test error propagation from backend to CLI
    - _Requirements: 13.1, 13.2_
  
  - [x] 13.2 Implement backend integration tests
    - Test agent orchestration workflows
    - Test state persistence and retrieval
    - Test audit logging
    - _Requirements: 13.1, 13.2_
  
  - [x] 13.3 Implement end-to-end scenario tests
    - Test complete user workflows (status, analyze, improve)
    - Test high-risk command confirmation flow
    - Test natural language command processing
    - _Requirements: 13.1, 13.2_

- [x] 14. Documentation and examples
  - [x] 14.1 Create API documentation
    - Document all REST endpoints
    - Include request/response examples
    - Document error codes and messages
    - _Requirements: 12.6_
  
  - [x] 14.2 Create user guide
    - Getting started tutorial
    - Command reference
    - Configuration guide
    - Troubleshooting guide
    - _Requirements: 12.6_
  
  - [x] 14.3 Create developer guide
    - Extension points documentation
    - Custom intent creation guide
    - Custom agent implementation guide
    - Plugin development guide
    - _Requirements: 11.6, 12.6_
  
  - [x] 14.4 Create example implementations
    - Example custom intent
    - Example custom agent
    - Example LLM provider implementation
    - Example vector store implementation
    - _Requirements: 11.7_

## Notes

- Tasks marked with `*` are optional property-based tests
- Tasks marked with `[x]` are completed
- Tasks marked with `[ ]` are pending
- Each task includes references to the requirements it validates
- Property tests should run with minimum 100 iterations
- All code must pass linting and formatting checks before completion