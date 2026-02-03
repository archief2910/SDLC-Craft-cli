# Development Guide

This guide covers development workflows, testing strategies, and best practices for SDLCraft CLI.

## Table of Contents

1. [Development Environment Setup](#development-environment-setup)
2. [Development Workflow](#development-workflow)
3. [Testing Strategy](#testing-strategy)
4. [Code Quality Standards](#code-quality-standards)
5. [Debugging](#debugging)
6. [Common Tasks](#common-tasks)

## Development Environment Setup

### Prerequisites

Install the following tools:

- **Go 1.21+**: [Download](https://go.dev/dl/)
- **Java 17+**: [Download](https://adoptium.net/)
- **Maven 3.8+**: [Download](https://maven.apache.org/download.cgi)
- **PostgreSQL 14+**: [Download](https://www.postgresql.org/download/)
- **Git**: [Download](https://git-scm.com/downloads)

### IDE Recommendations

**For Go (CLI)**:
- Visual Studio Code with Go extension
- GoLand

**For Java (Backend)**:
- IntelliJ IDEA (Community or Ultimate)
- Eclipse with Spring Tools

### Initial Setup

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
   
   # Verify connection
   psql -U sdlcraft -d sdlcraft -c "SELECT version();"
   ```

3. **Build CLI**
   ```bash
   cd cli
   go mod download
   go build -o sdlc
   ```

4. **Build Backend**
   ```bash
   cd backend
   mvn clean install
   ```

5. **Run Backend**
   ```bash
   mvn spring-boot:run
   ```
   
   Verify: `curl http://localhost:8080/actuator/health`

6. **Test CLI**
   ```bash
   cd cli
   ./sdlc --help
   ```

## Development Workflow

### Incremental Development Path

Follow this order to build components incrementally:

1. ✅ **Monorepo structure and scaffolding** (Task 1)
2. **CLI command parser and grammar** (Task 2)
3. **Deterministic command repair engine** (Task 3)
4. **Backend client communication** (Task 4)
5. **Output renderer** (Task 5)
6. **Intent inference service** (Task 6)
7. **Agent framework and orchestrator** (Task 7)
8. **SDLC state machine** (Task 8)
9. **Policy engine** (Task 9)
10. **Long-term memory** (Task 10)

### Branch Strategy

- `main`: Production-ready code
- `develop`: Integration branch for features
- `feature/<task-name>`: Individual feature branches

### Commit Messages

Follow conventional commits:

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types**:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `test`: Adding or updating tests
- `refactor`: Code refactoring
- `perf`: Performance improvements
- `chore`: Build process or auxiliary tool changes

**Examples**:
```
feat(cli): implement command parser with grammar validation

- Add Command struct with Raw, Intent, Target, Modifiers
- Implement Parser interface with Parse() and ValidateGrammar()
- Support both structured and natural language input detection

Validates: Requirements 2.1
```

## Testing Strategy

### Dual Testing Approach

SDLCraft uses **both unit tests and property-based tests**:

1. **Unit Tests**: Verify specific examples, edge cases, error conditions
2. **Property Tests**: Verify universal properties across all inputs

Both are necessary and complementary.

### Unit Testing

**CLI (Go)**:
```bash
cd cli
go test ./... -v
```

**Backend (Java)**:
```bash
cd backend
mvn test
```

**Writing Unit Tests**:

```go
// CLI example
func TestParser_ParseStructuredCommand(t *testing.T) {
    parser := NewParser()
    cmd, err := parser.Parse("sdlc status")
    
    assert.NoError(t, err)
    assert.Equal(t, "status", cmd.Intent)
    assert.Nil(t, cmd.Target)
}
```

```java
// Backend example
@Test
void testIntentInference_StatusCommand() {
    IntentRequest request = new IntentRequest("sdlc status");
    IntentResult result = intentService.inferIntent(request);
    
    assertThat(result.getIntent()).isEqualTo("status");
    assertThat(result.getConfidence()).isGreaterThan(0.9);
}
```

### Property-Based Testing

**CLI (Go with gopter)**:
```bash
cd cli
go test -tags=property ./... -v
```

**Backend (Java with jqwik)**:
```bash
cd backend
mvn test -Dtest=**/*PropertyTest
```

**Writing Property Tests**:

```go
// CLI example - Property 1: Command Repair Attempts
// Feature: sdlcraft-cli, Property 1: Command Repair Attempts
func TestProperty_CommandRepairAttempts(t *testing.T) {
    properties := gopter.NewProperties(nil)
    
    properties.Property("repairs commands with typos", prop.ForAll(
        func(validIntent string, typo string) bool {
            cmd := generateCommandWithTypo(validIntent, typo)
            result, err := repairEngine.Repair(cmd)
            
            return err == nil && (result.Repaired != nil || result.InferenceServiceCalled)
        },
        gen.OneConstOf("status", "analyze", "improve", "test", "debug"),
        gen.Identifier(),
    ))
    
    properties.TestingRun(t, gopter.ConsoleReporter(false))
}
```

```java
// Backend example - Property 11: State Persistence Round-Trip
// Feature: sdlcraft-cli, Property 11: State Persistence Round-Trip
@Property
void statePersistenceRoundTrip(@ForAll("sdlcStates") SDLCState originalState) {
    stateMachine.updateState(PROJECT_ID, originalState);
    SDLCState retrievedState = stateMachine.getCurrentState(PROJECT_ID);
    
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

### Property Test Configuration

- **Minimum iterations**: 100 per property test
- **Tag format**: `Feature: sdlcraft-cli, Property {number}: {property_text}`
- **Reference**: Each property must reference its design document property

### Integration Testing

Test CLI-Backend integration:

```bash
# Start backend
cd backend
mvn spring-boot:run &

# Run CLI integration tests
cd cli
go test -tags=integration ./... -v
```

### Test Coverage

**Target Coverage**:
- CLI: 80%+ line coverage
- Backend: 85%+ line coverage
- Critical paths: 100% coverage

**Generate Coverage Reports**:

```bash
# CLI
cd cli
go test -coverprofile=coverage.out ./...
go tool cover -html=coverage.out

# Backend
cd backend
mvn test jacoco:report
open target/site/jacoco/index.html
```

## Code Quality Standards

### Go (CLI)

**Formatting**:
```bash
gofmt -w .
```

**Linting**:
```bash
golangci-lint run
```

**Best Practices**:
- Use strong typing (avoid `interface{}` unless necessary)
- Handle all errors explicitly
- Add comments explaining WHY, not WHAT
- Keep functions small and focused
- Use meaningful variable names

### Java (Backend)

**Formatting**:
```bash
mvn spring-javaformat:apply
```

**Linting**:
```bash
mvn checkstyle:check
```

**Best Practices**:
- Use dependency injection (constructor injection preferred)
- Use interfaces for major components
- Add Javadoc for public APIs
- Use Optional for nullable returns
- Prefer immutable objects where possible

### General Standards

**No Placeholder Logic**:
- ❌ `// TODO: implement this`
- ❌ `throw new UnsupportedOperationException("Not implemented yet")`
- ✅ Complete implementations or clearly marked as future work

**No Hardcoded Data**:
- ❌ Hardcoded LLM responses
- ❌ Mock data in production code
- ✅ Use configuration files or environment variables

**Comments Explain WHY**:
```go
// ❌ Bad: Increment counter
counter++

// ✅ Good: Track number of repair attempts for metrics
counter++
```

## Debugging

### CLI Debugging

**Enable Verbose Mode**:
```bash
./sdlc --verbose status
```

**Debug with Delve**:
```bash
dlv debug main.go -- status
```

### Backend Debugging

**Enable Debug Logging**:
```yaml
# application.yml
logging:
  level:
    com.sdlcraft: DEBUG
```

**Remote Debugging**:
```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
```

Then attach your IDE debugger to port 5005.

### Database Debugging

**View Current State**:
```sql
-- Check SDLC state
SELECT * FROM sdlc_state;

-- Check recent commands
SELECT * FROM command_executions ORDER BY started_at DESC LIMIT 10;

-- Check audit logs
SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 10;
```

**Reset Database**:
```bash
psql -U sdlcraft -d sdlcraft -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
mvn spring-boot:run  # Will re-run migrations
```

## Common Tasks

### Add a New Intent

1. **Register in database**:
   ```sql
   INSERT INTO intent_definitions (name, description, required_parameters, optional_parameters, examples, default_risk_level)
   VALUES ('deploy', 'Deploy application to environment', '["environment"]', '["version"]', '["sdlc deploy staging"]', 'HIGH');
   ```

2. **Add CLI command** (if needed):
   ```go
   // cli/cmd/deploy.go
   var deployCmd = &cobra.Command{
       Use:   "deploy [environment]",
       Short: "Deploy application to environment",
       Run: func(cmd *cobra.Command, args []string) {
           // Implementation
       },
   }
   ```

3. **Add backend handler**:
   ```java
   // backend/src/main/java/com/sdlcraft/backend/intent/DeployIntentHandler.java
   @Component
   public class DeployIntentHandler implements IntentHandler {
       @Override
       public String getIntentName() {
           return "deploy";
       }
       
       @Override
       public ExecutionPlan createPlan(IntentResult intent, SDLCState state) {
           // Implementation
       }
   }
   ```

### Add a New Agent

1. **Implement Agent interface**:
   ```java
   @Component
   public class SecurityScannerAgent implements Agent {
       @Override
       public AgentResult plan(AgentContext context) {
           // Create security scan plan
       }
       
       @Override
       public AgentResult act(AgentContext context, Plan plan) {
           // Execute security scans
       }
       
       @Override
       public AgentResult observe(AgentContext context, ActionResult result) {
           // Validate scan results
       }
       
       @Override
       public AgentResult reflect(AgentContext context, ObservationResult observation) {
           // Analyze findings and suggest fixes
       }
   }
   ```

2. **Register with orchestrator**:
   ```java
   @Configuration
   public class AgentConfiguration {
       @Bean
       public AgentOrchestrator agentOrchestrator(List<Agent> agents) {
           AgentOrchestrator orchestrator = new AgentOrchestrator();
           agents.forEach(agent -> orchestrator.registerAgent(agent.getType(), agent));
           return orchestrator;
       }
   }
   ```

### Add a Database Migration

1. **Create migration file**:
   ```bash
   # backend/src/main/resources/db/migration/V2__add_feature.sql
   ```

2. **Write migration**:
   ```sql
   -- Add new column
   ALTER TABLE sdlc_state ADD COLUMN deployment_frequency INTEGER DEFAULT 0;
   
   -- Create new table
   CREATE TABLE deployment_history (
       id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
       project_id VARCHAR(255) NOT NULL,
       environment VARCHAR(50) NOT NULL,
       version VARCHAR(50) NOT NULL,
       deployed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
       CONSTRAINT fk_deployment_project FOREIGN KEY (project_id) REFERENCES sdlc_state(project_id)
   );
   ```

3. **Restart backend** (Flyway will auto-apply):
   ```bash
   mvn spring-boot:run
   ```

### Run End-to-End Test

1. **Start backend**:
   ```bash
   cd backend
   mvn spring-boot:run
   ```

2. **Run CLI command**:
   ```bash
   cd cli
   ./sdlc status
   ```

3. **Verify in database**:
   ```sql
   SELECT * FROM command_executions ORDER BY started_at DESC LIMIT 1;
   ```

## Troubleshooting

### CLI won't connect to backend

**Check**:
1. Backend is running: `curl http://localhost:8080/actuator/health`
2. Port is correct in CLI configuration
3. No firewall blocking connection

### Database connection failed

**Check**:
1. PostgreSQL is running: `pg_isready`
2. Database exists: `psql -l | grep sdlcraft`
3. User has permissions: `psql -U sdlcraft -d sdlcraft -c "SELECT 1;"`
4. Connection string in `application.yml` is correct

### Tests failing

**Check**:
1. Database is clean: Reset with migrations
2. Dependencies are up to date: `go mod download` / `mvn clean install`
3. Test isolation: Each test should clean up after itself
4. Property test iterations: Increase for more thorough testing

## Resources

- [Go Documentation](https://go.dev/doc/)
- [Cobra Documentation](https://cobra.dev/)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [gopter Documentation](https://github.com/leanovate/gopter)
- [jqwik Documentation](https://jqwik.net/)

## Getting Help

- Check [requirements.md](../.kiro/specs/sdlcraft-cli/requirements.md) for feature specifications
- Check [design.md](../.kiro/specs/sdlcraft-cli/design.md) for architecture details
- Check [tasks.md](../.kiro/specs/sdlcraft-cli/tasks.md) for implementation progress
- Open an issue on GitHub for bugs or questions
