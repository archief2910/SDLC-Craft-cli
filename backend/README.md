# SDLCraft Backend Component

This directory contains the Spring Boot backend service of SDLCraft.

## Structure

```
backend/
├── src/
│   ├── main/
│   │   ├── java/com/sdlcraft/backend/
│   │   │   ├── BackendApplication.java    # Main application class
│   │   │   ├── intent/                    # Intent inference service (to be added)
│   │   │   ├── agent/                     # Agent orchestrator (to be added)
│   │   │   ├── state/                     # SDLC state machine (to be added)
│   │   │   ├── policy/                    # Policy engine (to be added)
│   │   │   ├── memory/                    # Long-term memory (to be added)
│   │   │   └── api/                       # REST API controllers (to be added)
│   │   └── resources/
│   │       ├── application.yml            # Application configuration
│   │       └── db/migration/              # Flyway database migrations
│   │           └── V1__initial_schema.sql # Initial database schema
│   └── test/                              # Unit and property tests (to be added)
└── pom.xml                                # Maven dependencies
```

## Prerequisites

- Java 17 or higher
- Maven 3.8+
- PostgreSQL 14+

## Database Setup

```bash
# Create database
createdb sdlcraft

# Create user
psql sdlcraft -c "CREATE USER sdlcraft WITH PASSWORD 'sdlcraft';"
psql sdlcraft -c "GRANT ALL PRIVILEGES ON DATABASE sdlcraft TO sdlcraft;"
```

## Building

```bash
mvn clean install
```

## Running

```bash
mvn spring-boot:run
```

The backend will start on `http://localhost:8080` and automatically run Flyway migrations.

## Testing

```bash
# All tests
mvn test

# Property-based tests only
mvn test -Dtest=**/*PropertyTest

# Specific test
mvn test -Dtest=IntentInferenceServiceTest
```

## Configuration

Edit `src/main/resources/application.yml` to configure:
- Database connection
- Agent execution settings
- Memory retention
- Policy rules

## API Endpoints

(To be implemented)

- `POST /api/intent/infer` - Infer intent from natural language
- `POST /api/intent/execute` - Execute intent with agent orchestration
- `GET /api/state/{projectId}` - Query SDLC state
- `POST /api/state/{projectId}` - Update SDLC state

## Database Schema

The initial schema (V1__initial_schema.sql) includes:

- **sdlc_state**: Project SDLC state tracking
- **command_executions**: Command execution history
- **audit_logs**: Comprehensive audit trail
- **agent_executions**: Individual agent execution tracking
- **project_context**: Project-specific context
- **intent_definitions**: Registered intent definitions

## Design Principles

1. **Interface-Based**: All major components use interfaces (LLMProvider, Agent, VectorStore)
2. **Dependency Injection**: Spring manages all component lifecycles
3. **Explainable**: Every decision includes traceable reasoning
4. **Safe**: Policy engine enforces confirmation for high-risk operations
5. **Testable**: Components can be tested independently with mocks

## Next Steps

See [tasks.md](../.kiro/specs/sdlcraft-cli/tasks.md) for implementation tasks:
- Task 6: Implement intent inference service
- Task 7: Implement agent framework and orchestrator
- Task 8: Implement SDLC state machine
- Task 9: Implement policy engine
- Task 10: Implement long-term memory
