# CI Status & Known Issues

## Current Test Status

### Backend (Java/Maven)
- **Local Build**: ❌ Fails (requires Java 21, local has Java 11)
- **CI Build**: ✅ Will work (GitHub Actions uses Java 21)
- **Tests**: Need Java 21 to run

### CLI (Go)
- **Build**: ✅ Compiles successfully
- **Tests**: ❌ Some failures detected:
  - `main_test.go`: undefined rootCmd references (5 failures)
  - `client_test.go`: timeout test failure
  - `parser_property_test.go`: empty intent validation failure
  - `repair_decision_test.go`: multiple repair strategy failures
  - `repair_strategies_test.go`: flag normalization and typo correction failures

## What the CI Workflow Does

The `.github/workflows/ci.yml` will:

1. **Setup Environment**
   - Java 21 (Temurin distribution)
   - Go 1.21

2. **Run Tests**
   - Backend: `mvn -B -DskipTests=false test`
   - CLI: `go test ./...`
   - Linting: `golangci-lint run ./...`

3. **Security Scanning**
   - Trivy filesystem scan (HIGH + CRITICAL vulnerabilities)
   - SBOM generation with Syft

## Action Required Before CI Will Pass

### Fix Go Test Failures
The CLI has test failures that need to be fixed:

```bash
cd cli
go test ./... -v
```

Key issues:
- `main_test.go` references undefined `rootCmd` variable
- Parser property tests failing on edge cases
- Repair engine tests failing on specific strategies

### Verify Java Tests
Once you have Java 21 installed locally:

```bash
cd backend
mvn test
```

## CI Will Block Merges If:
- Maven tests fail
- Go tests fail
- golangci-lint reports issues
- Trivy finds HIGH or CRITICAL vulnerabilities

## Recommendation

Before enabling branch protection:
1. Fix the Go test failures in the CLI
2. Ensure all tests pass locally with Java 21
3. Push to a test branch and verify CI runs successfully
4. Then enable branch protection rules

## Testing the CI Locally

You can test parts of the CI locally:

```bash
# Backend tests (requires Java 21)
cd backend
mvn clean test

# CLI tests
cd cli
go test ./...

# CLI linting
cd cli
golangci-lint run ./...

# Security scan
trivy fs --severity HIGH,CRITICAL .
```
