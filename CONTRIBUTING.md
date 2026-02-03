# Contributing to SDLCraft CLI

Thank you for your interest in contributing to SDLCraft CLI! This document provides guidelines and instructions for contributing.

## Table of Contents

1. [Code of Conduct](#code-of-conduct)
2. [Getting Started](#getting-started)
3. [Development Process](#development-process)
4. [Coding Standards](#coding-standards)
5. [Testing Requirements](#testing-requirements)
6. [Pull Request Process](#pull-request-process)
7. [Issue Guidelines](#issue-guidelines)

## Code of Conduct

### Our Pledge

We are committed to providing a welcoming and inclusive environment for all contributors, regardless of experience level, background, or identity.

### Expected Behavior

- Be respectful and considerate
- Provide constructive feedback
- Focus on what is best for the project
- Show empathy towards other contributors

### Unacceptable Behavior

- Harassment or discriminatory language
- Personal attacks or trolling
- Publishing others' private information
- Other conduct that could reasonably be considered inappropriate

## Getting Started

### Prerequisites

Before contributing, ensure you have:

1. Read the [README.md](README.md) to understand the project
2. Read the [requirements.md](.kiro/specs/sdlcraft-cli/requirements.md) for feature specifications
3. Read the [design.md](.kiro/specs/sdlcraft-cli/design.md) for architecture details
4. Set up your development environment following [docs/setup-guide.md](docs/setup-guide.md)

### Finding Work

1. Check the [tasks.md](.kiro/specs/sdlcraft-cli/tasks.md) for current implementation status
2. Look for issues labeled `good first issue` or `help wanted`
3. Comment on an issue to express interest before starting work
4. Wait for maintainer approval before beginning implementation

## Development Process

### 1. Fork and Clone

```bash
# Fork the repository on GitHub
# Then clone your fork
git clone https://github.com/YOUR_USERNAME/sdlcraft-cli.git
cd sdlcraft-cli

# Add upstream remote
git remote add upstream https://github.com/sdlcraft/sdlcraft-cli.git
```

### 2. Create a Branch

```bash
# Update your fork
git checkout main
git pull upstream main

# Create a feature branch
git checkout -b feature/your-feature-name
```

Branch naming conventions:
- `feature/` - New features
- `fix/` - Bug fixes
- `docs/` - Documentation changes
- `test/` - Test additions or improvements
- `refactor/` - Code refactoring

### 3. Make Changes

Follow the [development guide](docs/development.md) for:
- Code structure and organization
- Testing requirements
- Documentation standards

### 4. Commit Changes

Use conventional commit messages:

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

**Example**:
```
feat(cli): implement command parser with grammar validation

- Add Command struct with Raw, Intent, Target, Modifiers
- Implement Parser interface with Parse() and ValidateGrammar()
- Support both structured and natural language input detection

Validates: Requirements 2.1
Closes: #42
```

### 5. Test Your Changes

**CLI Tests**:
```bash
cd cli
go test ./... -v
go test -tags=property ./... -v
```

**Backend Tests**:
```bash
cd backend
mvn test
mvn test -Dtest=**/*PropertyTest
```

**Integration Tests**:
```bash
# Start backend
cd backend
mvn spring-boot:run &

# Run CLI integration tests
cd cli
go test -tags=integration ./... -v
```

### 6. Push and Create PR

```bash
git push origin feature/your-feature-name
```

Then create a Pull Request on GitHub.

## Coding Standards

### Go (CLI)

**Formatting**:
```bash
gofmt -w .
```

**Linting**:
```bash
golangci-lint run
```

**Standards**:
- Use strong typing (avoid `interface{}` unless necessary)
- Handle all errors explicitly
- Add comments explaining WHY, not WHAT
- Keep functions small and focused (< 50 lines)
- Use meaningful variable names
- No placeholder logic or TODOs in production code

**Example**:
```go
// ✅ Good
// repairCommand attempts to fix typos in the command using edit distance.
// This allows users to work efficiently without memorizing exact syntax.
func repairCommand(cmd *Command) (*RepairResult, error) {
    if cmd == nil {
        return nil, fmt.Errorf("command cannot be nil")
    }
    
    // Use Levenshtein distance to find closest match
    distance := levenshtein(cmd.Intent, validIntents)
    if distance <= 2 {
        return &RepairResult{Repaired: closestMatch}, nil
    }
    
    return nil, fmt.Errorf("no repair found")
}

// ❌ Bad
// TODO: implement this
func repairCommand(cmd *Command) (*RepairResult, error) {
    return nil, nil
}
```

### Java (Backend)

**Formatting**:
```bash
mvn spring-javaformat:apply
```

**Standards**:
- Use dependency injection (constructor injection preferred)
- Use interfaces for major components
- Add Javadoc for public APIs
- Use Optional for nullable returns
- Prefer immutable objects where possible
- No placeholder logic or TODOs in production code

**Example**:
```java
// ✅ Good
/**
 * Infers structured intent from natural language input.
 * 
 * This service uses an LLM abstraction layer to convert ambiguous commands
 * into structured intents with confidence scores. It queries long-term memory
 * for similar past commands to improve accuracy.
 * 
 * @param request the intent inference request
 * @return structured intent with confidence and explanation
 */
@Service
public class IntentInferenceService {
    private final LLMProvider llmProvider;
    private final LongTermMemory memory;
    
    public IntentInferenceService(LLMProvider llmProvider, LongTermMemory memory) {
        this.llmProvider = llmProvider;
        this.memory = memory;
    }
    
    public IntentResult inferIntent(IntentRequest request) {
        // Query memory for similar past commands
        List<CommandExecution> similar = memory.querySimilar(request.getCommand(), 5);
        
        // Use LLM to infer intent with context
        String prompt = buildPrompt(request, similar);
        String response = llmProvider.complete(prompt, Map.of());
        
        return parseResponse(response);
    }
}

// ❌ Bad
@Service
public class IntentInferenceService {
    public IntentResult inferIntent(IntentRequest request) {
        // TODO: implement this
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
```

## Testing Requirements

### Dual Testing Approach

All contributions must include **both unit tests and property-based tests** where applicable.

### Unit Tests

**Required for**:
- All new functions, classes, and modules
- Bug fixes (test that reproduces the bug)
- Edge cases and error conditions

**Example**:
```go
func TestParser_ParseStructuredCommand(t *testing.T) {
    parser := NewParser()
    
    tests := []struct {
        name     string
        input    string
        wantIntent string
        wantTarget string
        wantErr  bool
    }{
        {"valid status", "sdlc status", "status", "", false},
        {"valid analyze", "sdlc analyze security", "analyze", "security", false},
        {"empty input", "", "", "", true},
    }
    
    for _, tt := range tests {
        t.Run(tt.name, func(t *testing.T) {
            cmd, err := parser.Parse(tt.input)
            if (err != nil) != tt.wantErr {
                t.Errorf("Parse() error = %v, wantErr %v", err, tt.wantErr)
                return
            }
            if !tt.wantErr {
                assert.Equal(t, tt.wantIntent, cmd.Intent)
                assert.Equal(t, tt.wantTarget, cmd.Target)
            }
        })
    }
}
```

### Property-Based Tests

**Required for**:
- Core logic that should work across all inputs
- State persistence and retrieval
- Command repair and inference
- Agent execution patterns

**Configuration**:
- Minimum 100 iterations per property test
- Tag format: `Feature: sdlcraft-cli, Property {number}: {property_text}`
- Reference design document property

**Example**:
```go
// Feature: sdlcraft-cli, Property 1: Command Repair Attempts
func TestProperty_CommandRepairAttempts(t *testing.T) {
    properties := gopter.NewProperties(nil)
    
    properties.Property("repairs commands with typos", prop.ForAll(
        func(validIntent string, typo string) bool {
            cmd := generateCommandWithTypo(validIntent, typo)
            result, err := repairEngine.Repair(cmd)
            
            // Property: repair should always be attempted
            return err == nil && (result.Repaired != nil || result.InferenceServiceCalled)
        },
        gen.OneConstOf("status", "analyze", "improve", "test", "debug"),
        gen.Identifier(),
    ))
    
    properties.TestingRun(t, gopter.ConsoleReporter(false))
}
```

### Test Coverage

**Minimum Requirements**:
- CLI: 80%+ line coverage
- Backend: 85%+ line coverage
- Critical paths: 100% coverage

**Check Coverage**:
```bash
# CLI
cd cli
go test -coverprofile=coverage.out ./...
go tool cover -html=coverage.out

# Backend
cd backend
mvn test jacoco:report
```

## Pull Request Process

### Before Submitting

- [ ] Code follows project coding standards
- [ ] All tests pass (unit and property tests)
- [ ] Test coverage meets minimum requirements
- [ ] Documentation is updated (if applicable)
- [ ] Commit messages follow conventional format
- [ ] No merge conflicts with main branch

### PR Description Template

```markdown
## Description
Brief description of changes

## Related Issue
Closes #123

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Testing
Describe the tests you added or modified

## Checklist
- [ ] Code follows project standards
- [ ] All tests pass
- [ ] Documentation updated
- [ ] No merge conflicts
```

### Review Process

1. **Automated Checks**: CI will run tests and linting
2. **Code Review**: Maintainers will review your code
3. **Feedback**: Address any requested changes
4. **Approval**: Once approved, maintainers will merge

### After Merge

- Delete your feature branch
- Update your fork:
  ```bash
  git checkout main
  git pull upstream main
  git push origin main
  ```

## Issue Guidelines

### Reporting Bugs

Use the bug report template:

```markdown
**Describe the bug**
A clear description of what the bug is.

**To Reproduce**
Steps to reproduce:
1. Run command '...'
2. See error

**Expected behavior**
What you expected to happen.

**Actual behavior**
What actually happened.

**Environment**
- OS: [e.g., Windows 11, macOS 13, Ubuntu 22.04]
- Go version: [e.g., 1.21.5]
- Java version: [e.g., 17.0.9]
- PostgreSQL version: [e.g., 14.10]

**Additional context**
Any other relevant information.
```

### Requesting Features

Use the feature request template:

```markdown
**Is your feature request related to a problem?**
A clear description of the problem.

**Describe the solution you'd like**
What you want to happen.

**Describe alternatives you've considered**
Other solutions you've thought about.

**Additional context**
Any other relevant information.
```

### Asking Questions

- Check existing issues and documentation first
- Use clear, descriptive titles
- Provide context and examples
- Be patient and respectful

## Recognition

Contributors will be recognized in:
- CONTRIBUTORS.md file
- Release notes
- Project README

Thank you for contributing to SDLCraft CLI! 🚀
