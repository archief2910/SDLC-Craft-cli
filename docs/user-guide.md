# SDLCraft CLI User Guide

## Table of Contents

1. [Getting Started](#getting-started)
2. [Command Reference](#command-reference)
3. [Configuration](#configuration)
4. [Troubleshooting](#troubleshooting)
5. [Best Practices](#best-practices)

---

## Getting Started

### What is SDLCraft CLI?

SDLCraft CLI is an intelligent command-line tool that helps you manage your Software Development Life Cycle (SDLC). It understands what you want to accomplish even when your commands aren't perfect, and it can execute complex workflows autonomously through AI agents.

Think of it as a senior engineer in your terminal - it corrects typos, interprets natural language, and prevents mistakes before they happen.

### Installation

#### Prerequisites

- Go 1.21 or higher (for CLI)
- Java 17 or higher (for backend)
- PostgreSQL 14 or higher
- Docker (optional, for containerized deployment)

#### Quick Install

**Option 1: From Source**

```bash
# Clone the repository
git clone https://github.com/your-org/sdlcraft.git
cd sdlcraft

# Build the CLI
cd cli
go build -o sdlc main.go

# Move to PATH (Linux/macOS)
sudo mv sdlc /usr/local/bin/

# Or add to PATH (Windows)
# Move sdlc.exe to a directory in your PATH
```

**Option 2: Using Docker**

```bash
# Pull the images
docker pull sdlcraft/cli:latest
docker pull sdlcraft/backend:latest

# Run with docker-compose
docker-compose up -d
```

#### Starting the Backend

```bash
cd backend

# Set up the database
createdb sdlcraft

# Run migrations (automatic on first start)
mvn spring-boot:run
```

The backend will start on `http://localhost:8080`.

#### Verify Installation

```bash
sdlc --version
# Output: SDLCraft CLI v1.0.0

sdlc status
# Should connect to backend and show project status
```

### Your First Command

Let's start with a simple status check:

```bash
sdlc status
```

Output:
```
✓ Project Status

Phase:              DEVELOPMENT
Risk Level:         MEDIUM
Test Coverage:      75%
Open Issues:        12
Release Readiness:  68%
Last Deployment:    5 days ago

Recommendations:
  • Increase test coverage to 80% for production readiness
  • Resolve 3 high-priority issues before staging
```

### Natural Language Commands

SDLCraft understands natural language. Try these:

```bash
# Instead of memorizing syntax, just say what you want
sdlc check the security of my project
sdlc how ready are we for release?
sdlc find performance problems
```

The CLI will interpret your intent and execute the appropriate command.

---

## Command Reference

### Core Command Structure

SDLCraft commands follow this pattern:

```
sdlc <intent> <target> [modifiers]
```

- **intent**: What you want to do (status, analyze, improve, test, debug, prepare, release)
- **target**: What you want to act on (security, performance, code, tests, etc.)
- **modifiers**: Additional options (--verbose, --format=json, etc.)

### Status Intent

Query the current state of your project.

**Basic Usage**:
```bash
sdlc status
```

**With Verbose Output**:
```bash
sdlc status --verbose
```

**JSON Format** (for scripting):
```bash
sdlc status --format=json
```

**Example Output**:
```json
{
  "projectId": "my-project",
  "currentPhase": "DEVELOPMENT",
  "riskLevel": "MEDIUM",
  "testCoverage": 0.75,
  "openIssues": 12,
  "releaseReadiness": 0.68
}
```

**Natural Language Alternatives**:
- `sdlc what's the status?`
- `sdlc show me the current state`
- `sdlc how's the project doing?`

---

### Analyze Intent

Perform analysis on your codebase or infrastructure.

#### Security Analysis

**Basic Usage**:
```bash
sdlc analyze security
```

**Deep Scan**:
```bash
sdlc analyze security --depth=full
```

**Example Output**:
```
✓ Security Analysis Complete

Vulnerabilities Found: 0
Security Score: 9.5/10

Dependencies Scanned: 127
  ✓ All dependencies up to date
  ✓ No known vulnerabilities

Recommendations:
  • Enable dependency scanning in CI/CD
  • Consider adding SAST tools
```

**Natural Language Alternatives**:
- `sdlc check for security issues`
- `sdlc scan for vulnerabilities`
- `sdlc is my code secure?`

#### Performance Analysis

**Basic Usage**:
```bash
sdlc analyze performance
```

**Example Output**:
```
✓ Performance Analysis Complete

Bottlenecks Identified: 2

1. Database Query in UserService.findAll()
   Impact: HIGH
   Current: 2.3s average
   Recommendation: Add index on user.email column
   Estimated Improvement: 85% faster

2. N+1 Query in OrderController.list()
   Impact: MEDIUM
   Current: 450ms average
   Recommendation: Use JOIN FETCH in query
   Estimated Improvement: 60% faster
```

**Natural Language Alternatives**:
- `sdlc find slow code`
- `sdlc what's making my app slow?`
- `sdlc performance problems`

---

### Improve Intent

Get suggestions for improving your code or processes.

**Basic Usage**:
```bash
sdlc improve performance
```

**Code Quality**:
```bash
sdlc improve code-quality
```

**Test Coverage**:
```bash
sdlc improve test-coverage
```

**Example Output**:
```
✓ Improvement Suggestions

Priority: HIGH
1. Add tests for UserService
   Current Coverage: 45%
   Target: 80%
   Estimated Effort: 4 hours
   
Priority: MEDIUM
2. Refactor OrderController
   Complexity: 8.5 (high)
   Recommendation: Extract business logic to service layer
   Estimated Effort: 2 hours
```

---

### Test Intent

Run tests or get testing recommendations.

**Run All Tests**:
```bash
sdlc test run
```

**Run Specific Test Suite**:
```bash
sdlc test run --suite=integration
```

**Get Test Recommendations**:
```bash
sdlc test suggest
```

---

### Debug Intent

Get help debugging issues.

**Analyze Recent Errors**:
```bash
sdlc debug errors
```

**Investigate Specific Issue**:
```bash
sdlc debug issue-123
```

---

### Prepare Intent

Prepare for deployment or release.

**Prepare for Staging**:
```bash
sdlc prepare staging
```

**Example Output**:
```
✓ Preparing for Staging Deployment

Checklist:
  ✓ All tests passing
  ✓ Code coverage above threshold (82%)
  ✓ No critical issues open
  ⚠ 2 high-priority issues open
  ✓ Database migrations ready
  ✓ Configuration validated

Blockers: None
Warnings: 2 high-priority issues should be reviewed

Ready to proceed? (yes/no): 
```

**Prepare for Production**:
```bash
sdlc prepare production
```

This is a **high-risk operation** and will require confirmation.

---

### Release Intent

Execute a release to an environment.

**Release to Staging**:
```bash
sdlc release staging
```

**Release to Production** (requires confirmation):
```bash
sdlc release production
```

**Example Confirmation Prompt**:
```
⚠ HIGH RISK OPERATION

You are about to release to PRODUCTION

Impact:
  • Affects all production users
  • Cannot be easily rolled back
  • Requires database migrations

Current State:
  Phase: STAGING
  Test Coverage: 85%
  Open Critical Issues: 0
  Release Readiness: 92%

Type 'yes' to confirm, or 'no' to cancel: 
```

---

### Command Repair

SDLCraft automatically fixes typos and common mistakes.

**Typo Correction**:
```bash
sdlc staus
# Auto-corrected to: sdlc status
✓ Corrected 'staus' → 'status'
```

**Flag Normalization**:
```bash
sdlc status -v
# Normalized to: sdlc status --verbose
```

**Ordering Fixes**:
```bash
sdlc security analyze
# Reordered to: sdlc analyze security
✓ Corrected command order
```

**Multiple Suggestions**:
```bash
sdlc analize
# Multiple possibilities detected:

Did you mean:
  1. analyze
  2. analyze security
  3. analyze performance

Select option (1-3): 
```

---

## Configuration

### CLI Configuration

The CLI configuration file is located at `~/.sdlcraft/config.yaml`.

**Default Configuration**:
```yaml
# Backend connection
backend:
  url: http://localhost:8080
  timeout: 30s
  retries: 3

# Output preferences
output:
  color: true
  verbose: false
  format: text  # text, json, yaml

# Behavior
behavior:
  auto_correct: true
  confirm_high_risk: true
  stream_output: true

# Project settings
project:
  default_id: my-project
  path: .
```

**Environment Variables**:

You can override configuration with environment variables:

```bash
export SDLC_BACKEND_URL=http://backend.example.com:8080
export SDLC_OUTPUT_FORMAT=json
export SDLC_PROJECT_ID=my-project
```

### Backend Configuration

The backend configuration is in `backend/src/main/resources/application.yml`.

**Key Settings**:

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/sdlcraft
    username: sdlcraft
    password: ${DB_PASSWORD}
  
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false

sdlcraft:
  llm:
    provider: mock  # Change to 'openai', 'anthropic', etc.
    api-key: ${LLM_API_KEY}
  
  vector-store:
    provider: mock  # Change to 'pinecone', 'weaviate', etc.
    
  memory:
    retention-days: 90
    
  policy:
    require-confirmation:
      - production
      - delete
      - reset
```

### Project Configuration

Create a `.sdlcraft.yaml` file in your project root:

```yaml
project:
  id: my-awesome-project
  name: My Awesome Project
  
phases:
  development:
    min-coverage: 0.70
  staging:
    min-coverage: 0.80
  production:
    min-coverage: 0.85
    
policies:
  - name: no-prod-without-tests
    rule: production requires coverage >= 0.85
    severity: critical
    
  - name: staging-approval
    rule: staging requires approval
    severity: high

custom-metrics:
  - name: code-quality-score
    threshold: 8.0
  - name: security-score
    threshold: 9.0
```

---

## Troubleshooting

### CLI Cannot Connect to Backend

**Symptom**:
```
✗ Error: Cannot connect to backend at http://localhost:8080
```

**Solutions**:

1. **Check if backend is running**:
   ```bash
   curl http://localhost:8080/actuator/health
   ```

2. **Verify backend URL in config**:
   ```bash
   cat ~/.sdlcraft/config.yaml | grep url
   ```

3. **Check firewall settings**:
   ```bash
   # Linux
   sudo ufw status
   
   # macOS
   sudo /usr/libexec/ApplicationFirewall/socketfilterfw --getglobalstate
   ```

4. **Try with explicit URL**:
   ```bash
   SDLC_BACKEND_URL=http://localhost:8080 sdlc status
   ```

---

### Command Not Recognized

**Symptom**:
```
✗ Error: Unknown command 'xyz'

Did you mean:
  • status
  • analyze
```

**Solutions**:

1. **Check for typos** - SDLCraft will suggest corrections
2. **Use natural language** - Try describing what you want:
   ```bash
   sdlc what can I do?
   ```
3. **List available intents**:
   ```bash
   sdlc help
   ```

---

### High-Risk Command Blocked

**Symptom**:
```
✗ Error: This operation requires confirmation but was not confirmed
```

**Solutions**:

1. **Run with confirmation**:
   ```bash
   sdlc release production
   # Then type 'yes' when prompted
   ```

2. **Use non-interactive mode** (CI/CD):
   ```bash
   echo "yes" | sdlc release production
   ```

3. **Check policy configuration** if you believe this shouldn't require confirmation

---

### Slow Performance

**Symptom**: Commands take a long time to execute

**Solutions**:

1. **Check backend logs**:
   ```bash
   tail -f backend/logs/application.log
   ```

2. **Verify database performance**:
   ```sql
   SELECT * FROM pg_stat_activity WHERE state = 'active';
   ```

3. **Check LLM provider latency** - If using external LLM, network latency may be high

4. **Enable caching** in configuration:
   ```yaml
   backend:
     cache:
       enabled: true
       ttl: 300s
   ```

---

### Database Connection Errors

**Symptom**:
```
✗ Error: DATABASE_UNAVAILABLE
```

**Solutions**:

1. **Verify PostgreSQL is running**:
   ```bash
   pg_isready -h localhost -p 5432
   ```

2. **Check credentials**:
   ```bash
   psql -U sdlcraft -d sdlcraft -h localhost
   ```

3. **Verify database exists**:
   ```bash
   psql -U postgres -c "\l" | grep sdlcraft
   ```

4. **Run migrations**:
   ```bash
   cd backend
   mvn flyway:migrate
   ```

---

### LLM Provider Errors

**Symptom**:
```
✗ Error: LLM_UNAVAILABLE - Falling back to template-based inference
```

**Solutions**:

1. **Check API key**:
   ```bash
   echo $LLM_API_KEY
   ```

2. **Verify provider configuration** in `application.yml`

3. **Test provider connection**:
   ```bash
   curl -H "Authorization: Bearer $LLM_API_KEY" \
        https://api.openai.com/v1/models
   ```

4. **Use mock provider for testing**:
   ```yaml
   sdlcraft:
     llm:
       provider: mock
   ```

---

## Best Practices

### 1. Use Natural Language

Don't memorize syntax - just describe what you want:

```bash
# Instead of:
sdlc analyze security --depth=full --format=json

# Try:
sdlc check my project for security issues
```

### 2. Let SDLCraft Fix Typos

Don't worry about perfect spelling:

```bash
sdlc staus  # Auto-corrects to 'status'
sdlc analize secrity  # Suggests corrections
```

### 3. Use Verbose Mode for Learning

When learning, use `--verbose` to see what's happening:

```bash
sdlc status --verbose
```

This shows agent reasoning and decision-making.

### 4. Review High-Risk Operations

Always review the impact before confirming high-risk operations:

```bash
sdlc release production
# Read the impact assessment carefully before typing 'yes'
```

### 5. Use JSON Format for Scripting

When using SDLCraft in scripts, use JSON output:

```bash
#!/bin/bash
COVERAGE=$(sdlc status --format=json | jq -r '.testCoverage')
if (( $(echo "$COVERAGE < 0.8" | bc -l) )); then
  echo "Coverage too low: $COVERAGE"
  exit 1
fi
```

### 6. Configure Project-Specific Settings

Create `.sdlcraft.yaml` in your project root for team-wide settings:

```yaml
project:
  id: team-project
  
phases:
  production:
    min-coverage: 0.90  # Stricter for this project
```

### 7. Monitor Audit Logs

Regularly review audit logs for high-risk operations:

```bash
sdlc audit logs --risk-level=HIGH --last=7d
```

### 8. Keep Backend Updated

Regularly update the backend to get new features and bug fixes:

```bash
cd backend
git pull
mvn clean install
```

### 9. Use Streaming for Long Operations

For long-running operations, streaming shows progress:

```bash
sdlc analyze security --stream
# Shows real-time progress instead of waiting for completion
```

### 10. Leverage Memory

SDLCraft learns from your commands. Reference past operations:

```bash
sdlc what did I do last time for security?
sdlc repeat the last analysis
```

---

## Getting Help

### In-CLI Help

```bash
# General help
sdlc help

# Help for specific intent
sdlc help analyze

# List all intents
sdlc intents list
```

### Documentation

- **API Reference**: `docs/api-reference.md`
- **Developer Guide**: `docs/developer-guide.md`
- **Architecture**: `docs/architecture.md`

### Community

- **GitHub Issues**: Report bugs and request features
- **Discussions**: Ask questions and share tips
- **Contributing**: See `CONTRIBUTING.md`

---

## Next Steps

Now that you understand the basics:

1. **Try different intents** - Experiment with analyze, improve, test
2. **Configure your project** - Create `.sdlcraft.yaml` with your team's standards
3. **Integrate with CI/CD** - Use SDLCraft in your deployment pipeline
4. **Extend with custom intents** - See the Developer Guide for creating custom intents

Happy coding! 🚀
